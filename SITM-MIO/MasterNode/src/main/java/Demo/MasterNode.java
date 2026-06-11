package Demo;

import java.io.File;
import java.util.Scanner;

import org.example.controller.MasterController;
import org.example.facade.SystemFacade;
import org.example.scheduler.JobScheduler;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class MasterNode {
    public static void main(String[] args) {
        printBanner();

        try (Communicator communicator = Util.initialize(args, "config.master")) {
            MasterController controller = new MasterController(communicator);
            JobScheduler scheduler = new JobScheduler(
                    controller,
                    communicator.getProperties().getPropertyWithDefault(
                            "Master.ActiveLinesFile",
                            System.getProperty("sitm.linesFile", "lines-241-ActiveGT.csv")
                    ),
                    communicator.getProperties().getPropertyWithDefault(
                            "Master.OutputCsv",
                            System.getProperty("sitm.outputCsv", "route_month_speeds.csv")
                    ),
                    Long.parseLong(communicator.getProperties().getPropertyWithDefault(
                            "Master.ChunkSizeBytes",
                            System.getProperty("sitm.chunkSizeBytes", Long.toString(JobScheduler.DEFAULT_CHUNK_SIZE_BYTES))
                    )),
                    Long.parseLong(communicator.getProperties().getPropertyWithDefault(
                            "Master.ChunkOverlapBytes",
                            System.getProperty("sitm.chunkOverlapBytes", Long.toString(JobScheduler.DEFAULT_CHUNK_OVERLAP_BYTES))
                    )),
                    Integer.parseInt(communicator.getProperties().getPropertyWithDefault(
                            "Master.RemoteReadSizeBytes",
                            System.getProperty("sitm.remoteReadSizeBytes", Integer.toString(JobScheduler.DEFAULT_REMOTE_READ_SIZE_BYTES))
                    )),
                    isEnabled(communicator.getProperties().getPropertyWithDefault("Master.Verbose", "false"))
            );

            String selectedFile = showFileMenu();
            if (selectedFile == null) {
                System.out.println("[Master] Operacion cancelada.");
                return;
            }

            File file = new File(selectedFile);
            if (!file.exists()) {
                System.err.println("[Master] ERROR: Archivo no encontrado: " + selectedFile);
                return;
            }

            ObjectAdapter fileProviderAdapter = communicator.createObjectAdapter("FileProviderAdapter");
            FileProviderPrx fileProvider = FileProviderPrx.uncheckedCast(
                    fileProviderAdapter.add(new FileProviderI(file), Util.stringToIdentity("MasterFileProvider"))
            );
            fileProviderAdapter.activate();

            System.out.println();
            System.out.println("[Master] Iniciando analisis distribuido ruta-mes");
            System.out.println("[Master] Archivo: " + file.getAbsolutePath());
            System.out.println("[Master] Tamano:  " + String.format("%.2f MB", file.length() / 1024.0 / 1024.0));
            System.out.println("[Master] Servicio de lectura remota: " + fileProvider);
            System.out.println();

            scheduler.setFileProvider(fileProvider);
            new SystemFacade(scheduler).startAnalysis(selectedFile);
        } catch (Exception e) {
            System.err.println("[Master] ERROR FATAL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("MASTER NODE - SITM-MIO");
        System.out.println("Calculo distribuido de velocidad promedio por ruta y mes");
        System.out.println();
    }

    private static boolean isEnabled(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String showFileMenu() {
        Scanner scanner = new Scanner(System.in);

        String[] files = {
                "../data/datagrams-MiniPilot.csv",
                "/home/swarch/Documents/data/datagrams4Pilot.csv"
        };

        String[] descriptions = {
                "MiniPilot local para prueba rapida",
                "Pilot final del enunciado"
        };

        System.out.println("[Master] Seleccione archivo para procesar");
        for (int i = 0; i < files.length; i++) {
            File file = new File(files[i]);
            String status = file.exists() ? "OK" : "NO ENCONTRADO";
            System.out.println(String.format("  [%d] %s - %s", i + 1, descriptions[i], status));
            System.out.println("      " + files[i]);
        }
        System.out.println("  [0] Ingresar ruta manualmente");
        System.out.print("Opcion: ");

        try {
            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 0) {
                System.out.print("Ingrese ruta completa del archivo: ");
                return scanner.nextLine().trim();
            }
            if (choice >= 1 && choice <= files.length) {
                return files[choice - 1];
            }
            System.err.println("[Master] Opcion invalida.");
            return null;
        } catch (Exception e) {
            System.err.println("[Master] Entrada invalida.");
            return null;
        }
    }
}
