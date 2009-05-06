package idp.epiduke_saxon;


import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import java.util.Stack;
import java.util.Vector;
import java.util.Map;

public class TCPMonitor implements Runnable {

    private final ServerSocket tcp;
    private Socket client;
    private final Templates tFactory;
    private final Stack<Transformer> transformers = new Stack<Transformer>();
    private final int threads;
    private Map<String,String> params;

    public TCPMonitor(int tcpPort, int threads, Templates tFactory, Map params) throws IOException {
        this.tcp = new ServerSocket(tcpPort);
        this.tFactory = tFactory;
        this.threads = threads;
        if (params != null) {
            this.params = params;
        }
    }

    public void run() {
        PrintWriter out = null;
        BufferedReader in = null;
        try {
            this.client = this.tcp.accept();
            out = new PrintWriter(
                          client.getOutputStream(), true);
            in = new BufferedReader(
                                    new InputStreamReader(
                                        client.getInputStream()));
        } catch (IOException e) {
            System.err.println("Accept failed on port " + this.tcp.getLocalPort() + ".");
            System.exit(1);
        }
        final Vector<String> data = new Vector<String>(200000, 100);
        ThreadGroup handlers = new ThreadGroup("saxon-handlers");
        for (int i = 0; i < this.threads; i++) {
            Thread handler = new Thread(handlers, getHandler(data));
            handler.start();
        }

        while (!tcp.isClosed()) {
            try {
                String SData = in.readLine();
                if ("FINISHED".equals(SData)) {
                    tcp.close();
                    synchronized (data) {
                        data.notifyAll();
                    }
                    break;
                } else if ("PING".equals(SData)) {
                    out.println("PONG");
                    continue;
                } else if ("DONE?".equals(SData)) {
                    if (data.size() == 0) {
                        out.println("DONE");
                    } else {
                        out.println(data.size() + " items remaining to be converted.");
                    }
                    continue;
                }
                if (SData != null) {
                    data.add(SData);
                    synchronized (data) {
                        data.notify();
                    }
                }
            } catch (IOException ioe) {
                System.err.println(ioe.toString());
                try {
                    tcp.close();
                } catch (IOException ioex) {
                    System.err.println(ioex);
                }
                break;
            }
        }

        Thread[] threads = new Thread[1];
        while (handlers.activeCount() > 0) {
            handlers.enumerate(threads);
            try {
                if (threads[0] != null) {
                    threads[0].join();
                }
            } catch (InterruptedException e) {
                System.err.println("Could not wait on handlers; exiting");
                handlers.interrupt();
                System.exit(1);
            }
        }
    }

    private Runnable getHandler(final Vector<String> data) {
        return new Runnable() {

            public void run() {
                while (true) {
                    String sData = null;
                    try {
                        synchronized (data) {
                            if (data.size() == 0) {
                                if (tcp.isClosed()) {
                                    return;
                                }
                                data.wait();
                                if (data.size() == 0) {
                                    return;
                                }
                            }
                            if (data.size() > 0) {
                                sData = data.remove(0);
                            }
                        }

                    } catch (InterruptedException e) {
                        System.err.println(e.toString());
                        e.printStackTrace();
                        return;
                    }

                    if (sData != null) {
                        int split = sData.indexOf(' ');
                        if (split > 0) {
                            String inPath = sData.substring(0, split);
                            String outPath = sData.substring(split + 1);
                            Transformer t = transformers.size() > 0 ? transformers.pop() : getTransformer();
                            File in = new File(inPath);
                            try {
                                StreamSource ss = new StreamSource(new FileInputStream(in));
                                FileOutputStream fos = new FileOutputStream(new File(outPath));
                                StreamResult sr = new StreamResult(fos);
                                t.transform(ss, sr);
                                transformers.add(t);
                                fos.flush();
                                fos.close();
                            } catch (Exception e) {
                                System.err.println(e.toString());
                                e.printStackTrace();
                                System.err.println("\tin:" + inPath + "\tout:" + outPath);
                                File out = new File(outPath);
                                out.delete();
                            }

                        } else {
                            System.err.println("Bad message: " + data);
                        }
                    }
                }
            }
        };
    }

    private Transformer getTransformer() {
        try {
            Transformer result = this.tFactory.newTransformer();
            if (this.params != null) {
                for (String key : params.keySet()) {
                    result.setParameter(key, this.params.get(key));
                }
            }
            return result;
        } catch (Throwable t) {
            System.err.println(t.toString());
            t.printStackTrace();
            return null;
        }
    }
}

