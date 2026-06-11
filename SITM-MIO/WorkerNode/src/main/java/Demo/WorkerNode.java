package Demo;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import java.net.InetAddress;

public class WorkerNode {
    public static void main(String[] args) {
        printBanner();

        try (Communicator communicator = Util.initialize(args, "config.worker")) {
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");

            com.zeroc.Ice.Object workerImpl = new WorkerI();
            adapter.add(workerImpl, Util.stringToIdentity("SimpleWorker"));
            adapter.activate();

            System.out.println("[Worker] Nodo activo");
            System.out.println("[Worker] Host:     " + getHostInfo());
            System.out.println("[Worker] Endpoint: " + adapter.getEndpoints()[0]);
            System.out.println("[Worker] Usuario:  " + System.getProperty("user.name"));
            System.out.println("[Worker] JVM:      " + System.getProperty("java.version"));
            System.out.println("[Worker] Estado:   esperando tareas del Master");
            System.out.println("[Worker] Nota:     procesa datagramas y devuelve acumulados ruta-mes");
            System.out.println();

            communicator.waitForShutdown();
            System.out.println("[Worker] Apagado correcto.");
        } catch (Exception e) {
            System.err.println("[Worker] ERROR FATAL: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("WORKER NODE - SITM-MIO");
        System.out.println("Calculo distribuido de velocidad promedio por ruta y mes");
        System.out.println();
    }

    private static String getHostInfo() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName() + " (" + addr.getHostAddress() + ")";
        } catch (Exception e) {
            return "localhost";
        }
    }
}
