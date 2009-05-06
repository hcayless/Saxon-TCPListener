package idp.epiduke_saxon;


import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.StandardErrorListener;
import net.sf.saxon.StandardURIResolver;


public class Main {

    private static Thread monitor;
    private static Map<String,String> parameters;

    public static void main(String[] args) {
        try {
            String xsl = null;
            int port = -1;
            int threads = 6;
            for (int i = 0; i < args.length - 1; i += 2) {
                if ("--port".equals(args[i])) {
                    port = Integer.parseInt(args[i + 1]);
                }
                if ("--xsl".equals(args[i])) {
                    xsl = args[i + 1];
                }
                if ("--threads".equals(args[i])) {
                    threads = Integer.parseInt(args[i + 1]);
                }
                if ("--help".equals(args[i])) {
                    printUsage();
                    System.exit(0);
                }
                if ("--params".equals(args[i])) {
                    parameters = new HashMap<String,String>();
                    try {
                        String params = args[i+1];
                        String[] items = params.split(":");
                        for (String item : items) {
                            String[] kv = item.split("=");
                            parameters.put(kv[0], kv[1]);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        parameters.clear();
                    }
                }
            }
            if (port == -1 || xsl == null) {
                printUsage();
                System.exit(0);
            }

            StreamSource xslSrc = new StreamSource(new FileInputStream(xsl));
            xslSrc.setSystemId(xsl);
            javax.xml.transform.Templates tFactory;
            Configuration configuration = new Configuration();
            CompilerInfo compilerInfo = new CompilerInfo();
            compilerInfo.setErrorListener(new StandardErrorListener());
            compilerInfo.setURIResolver(new StandardURIResolver(configuration));
            tFactory = (Templates)PreparedStylesheet.compile(xslSrc, configuration, compilerInfo);


            Thread monit = new Thread(new TCPMonitor(port, threads, tFactory, parameters));
            monit.setDaemon(false);
            monit.start();
            Main.monitor = monit;
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public static boolean join() {
        if (Main.monitor == null) {
            return false;
        }
        try {
            Main.monitor.join(0);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static void printUsage() {
        System.out.println("--xsl     : path to a file with the xsl to be applied");
        System.out.println("--port    : port the data monitor will listen on");
        System.out.println("--threads : the number of concurrent threads.  Default 6.");
        System.out.println("--params  : parameters for the XSLT, in the form key1=value1:key2=value2");
        System.out.println("--help    : print this help");
    }
}

