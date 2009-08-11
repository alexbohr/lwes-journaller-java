package org.lwes.journaller;
/**
 * User: fmaritato
 * Date: Apr 14, 2009
 */

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lwes.EventSystemException;
import org.lwes.journaller.handler.AbstractFileEventHandler;
import org.lwes.journaller.handler.GZIPEventHandler;
import org.lwes.journaller.handler.NIOEventHandler;
import org.lwes.listener.DatagramEventListener;
import org.lwes.listener.EventHandler;

import java.io.IOException;
import java.net.InetAddress;

public class Journaller implements Runnable {

    private static transient Log log = LogFactory.getLog(Journaller.class);

    private String fileName;
    private String multicastAddress = "224.1.1.11";
    private String multicastInterface;
    private int port = 12345;
    private int ttl = 1;
    private EventHandler eventHandler = null;
    private DatagramEventListener listener = null;
    private boolean useGzip = false;
    private boolean initialized = false;
    private static Options options;

    static {
        options = new Options();
        options.addOption("f", "file", true, "File to write events to.");
        options.addOption("m", "multicast-address", true, "Multicast address.");
        options.addOption("p", "port", true, "Multicast Port.");
        options.addOption("i", "interface", true, "Multicast Interface.");
        options.addOption("t", "ttl", true, "Set the Time-To-Live on the socket.");
        options.addOption("h", "help", false, "Print this message.");
        options.addOption(null, "gzip", false, "Use the gzip event handler. NIO is used by default.");
    }

    public Journaller() {
    }

    public void initialize() throws EventSystemException, IOException {
        if (useGzip) {
            eventHandler = new GZIPEventHandler(getFileName());
        }
        else {
            eventHandler = new NIOEventHandler(getFileName());
        }
        InetAddress address = InetAddress.getByName(getMulticastAddress());
        InetAddress iface = null;
        if (getMulticastInterface() != null) {
            iface = InetAddress.getByName(getMulticastInterface());
        }
        listener = new DatagramEventListener();
        listener.setAddress(address);
        if (iface != null) {
            listener.setInterface(iface);
        }
        listener.setPort(getPort());
        listener.addHandler(eventHandler);
        if (ttl > 0) {
            listener.setTimeToLive(ttl);
        }
        listener.initialize();

        // Add a shutdown hook in case of kill or ^c
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(eventHandler));

        if (log.isInfoEnabled()) {
            log.info("LWES Journaller");
            log.info("Multicast Address: " + getMulticastAddress());
            log.info("Multicast Interface: " + getMulticastInterface());
            log.info("Multicast Port: " + getPort());
            log.info("Using event hander: " + getEventHandler().getClass().getName());
        }

        initialized = true;
    }

    public void run() {

        try {
            if (!initialized) {
                initialize();
            }

            // keep this thread busy
            while (true) {
                try {
                    Thread.sleep(1000);
                }
                catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        catch (Exception e) {
            log.error("Error initializing: ", e);
        }
    }

    public static void main(String[] args) {
        Journaller j = new Journaller();

        try {
            CommandLineParser parser = new PosixParser();
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("h") || line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("lwes-journaller", options);
                Runtime.getRuntime().exit(1);
            }
            if (line.hasOption("f") || line.hasOption("file")) {
                j.setFileName(line.getOptionValue("f") == null ?
                              line.getOptionValue("file") :
                              line.getOptionValue("f"));
            }
            if (line.hasOption("m") || line.hasOption("multicast-address")) {
                j.setMulticastAddress(line.getOptionValue("m") == null ?
                                      line.getOptionValue("multicast-address") :
                                      line.getOptionValue("m"));
            }
            if (line.hasOption("p") || line.hasOption("port")) {
                j.setPort(Integer.parseInt(line.getOptionValue("p") == null ?
                                           line.getOptionValue("port") :
                                           line.getOptionValue("p")));
            }
            if (line.hasOption("i") || line.hasOption("interface")) {
                j.setMulticastInterface(line.getOptionValue("i") == null ?
                                        line.getOptionValue("interface") :
                                        line.getOptionValue("i"));
            }
            if (line.hasOption("t") || line.hasOption("ttl")) {
                j.setTtl(Integer.parseInt(line.getOptionValue("t") == null ?
                                          line.getOptionValue("ttl") :
                                          line.getOptionValue("t")));
            }
            if (line.hasOption("gzip")) {
                j.setUseGzip(true);
            }

            j.run();
        }
        catch (NumberFormatException e) {
            log.error(e);
        }
        catch (ParseException e) {
            log.error(e);
        }
    }

    class ShutdownThread extends Thread {

        EventHandler eventHandler;

        ShutdownThread(EventHandler eh) {
            eventHandler = eh;
        }

        public void run() {
            log.debug("shutdown thread run()");
            eventHandler.destroy();
            try {
                listener.shutdown();
            }
            catch (EventSystemException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public boolean isUseGzip() {
        return useGzip;
    }

    public void setUseGzip(boolean useGzip) {
        this.useGzip = useGzip;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    public void setMulticastAddress(String multicastAddress) {
        this.multicastAddress = multicastAddress;
    }

    public String getMulticastInterface() {
        return multicastInterface;
    }

    public void setMulticastInterface(String iface) {
        this.multicastInterface = iface;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public EventHandler getEventHandler() {
        return eventHandler;
    }

    public void setEventHandler(AbstractFileEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
