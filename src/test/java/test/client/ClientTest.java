package test.client;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Create the client ear, unpack it and run the client
 */
@ExtendWith(ArquillianExtension.class)
public class ClientTest {

    @TargetsContainer("glassfish")
    @Deployment(name = "app-client", order = 2)
    public static EnterpriseArchive createDeploymentVehicle() throws IOException {
        // Client jar
        // the war with the correct archive name
        JavaArchive clientJar = ShrinkWrap.create(JavaArchive.class, "client-main.jar");
        // The class files
        clientJar.addClasses(ClientMain.class);
        clientJar.addAsManifestResource(new StringAsset("Main-Class: " + ClientMain.class.getName() + "\n"), "MANIFEST.MF");

        // Add a dummy war or GF creates a test war that produces an invalid application.xml
        WebArchive war = ShrinkWrap.create(WebArchive.class, "client-web.war");
        URL webResURL = ClientTest.class.getResource("/index.html");
        war.addAsResource(webResURL, "index.html");

        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, "app-client.ear");
        ear.addAsModule(clientJar);
        ear.addAsModule(war);
        URL earResURL = ClientTest.class.getResource("/application.xml");
        ear.addAsManifestResource(earResURL, "application.xml");

        // Unpack the ear for the appclient runner
        Path earPath = Paths.get("target", "app-client.ear");
        Files.createDirectories(earPath);
        Path clientJarPath = earPath.resolve("client-main.jar");
        final ZipExporter zipExporter = clientJar.as(ZipExporter.class);
        zipExporter.exportTo(clientJarPath.toFile(), true);

        return ear;
    }

    @Test
    @RunAsClient
    public void testAppClient() throws Exception {
        System.out.println("Running testAppClient");
        runClient();
        System.out.println("Finished testAppClient");
    }

    private void runClient() throws Exception {
        String glassfishHome = System.getProperty("glassfish.home");

        Files.list(Paths.get("target/app-client.ear")).forEach(path -> {
            System.out.println("Unpacked file: " + path);
        });
        File clientDir = null;
        String[] clientCmdLine = {
                glassfishHome+"/glassfish/bin/appclient",
                "-jar",
                "target/app-client.ear/client-main.jar"
        };
        String[] clientEnvp = null;

        Process appClientProcess = Runtime.getRuntime().exec(clientCmdLine, clientEnvp, clientDir);
        System.out.println("Created process" + appClientProcess.info());
        System.out.println("process(%d).envp: %s".formatted(appClientProcess.pid(), Arrays.toString(clientEnvp)));
        BufferedReader outputReader = new BufferedReader(new InputStreamReader(appClientProcess.getInputStream(), StandardCharsets.UTF_8));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(appClientProcess.getErrorStream(), StandardCharsets.UTF_8));

        final Thread readOutputThread = new Thread(() -> readClientProcess(outputReader, false), "stdout reader");
        readOutputThread.start();
        final Thread readErrorThread = new Thread(() -> readClientProcess(errorReader, true), "stderr reader");
        readErrorThread.start();
        System.out.println("Started process reader threads");

        boolean timeout = appClientProcess.waitFor(1000, TimeUnit.SECONDS);
        if (timeout) {
            System.out.println("AppClient process finished");
        } else {
            System.out.println("AppClient process timed out");
            appClientProcess.destroy();
            throw new RuntimeException("AppClient process timed out");
        }
        Assertions.assertEquals(0, appClientProcess.exitValue(), "AppClient process exited with non-zero code");
    }
    private void readClientProcess(BufferedReader reader, boolean errReader) {
        System.out.println("Begin readClientProcess");
        int count = 0;
        try {
            String line = reader.readLine();
            // System.out.println("RCP: " + line);
            while (line != null) {
                count++;
                if (errReader)
                    System.out.println("[stderr] " + line);
                else
                    System.out.println("[stdout] " + line);
                line = reader.readLine();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println(String.format("Exiting(isStderr=%s), read %d lines", errReader, count));
    }

}
