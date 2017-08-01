package net.networkdowntime.morris.server;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.PropertySource;

import net.networkdowntime.morris.Constants;
import net.networkdowntime.morris.ModeTwoRunner;
import net.networkdowntime.morris.MoveHandler;
import net.networkdowntime.morris.Utils;

@SpringBootApplication
@PropertySource(value = { "classpath:application.properties" })
public class WebServerApplication {

    private enum Mode {
        mode1, mode2
    };

    public static void main(String[] args) throws IOException {
        Mode mode = getMode(args);

        if (mode == Mode.mode1) doMode1(args);
        else if (mode == Mode.mode2) doMode2(args);
    }

    private static Mode getMode(String... args) {
        for (String arg : args) {
            if (Mode.mode1.toString().equals(arg)) return Mode.mode1;
            else if (Mode.mode2.toString().equals(arg)) return Mode.mode2;
        }
        System.err.println("Missing Wrapper Mode Argument: valid wrapper modes are [mode1|mode2]");
        System.exit(1);
        return null;
    }

    private static void doMode2(String... args) throws IOException {
        boolean fail = false;

        String username = null;
        String password = null;
        String server = null;

        int argNum = 1; // arg 1 is the mode
        // Read Username
        if (args.length > argNum) {
            username = args[argNum];
        } else {
            System.err.println("Missing Username Argument: expected your username as argument " + argNum);
            fail |= true;
        }
        argNum++;
        // Read Password
        if (args.length > argNum) {
            password = args[argNum];
        } else {
            System.err.println("Missing Password Argument: expected your password as argument " + argNum);
            fail |= true;
        }
        argNum++;
        // Read Server URL
        if (args.length > argNum) {
            server = args[argNum];
        } else {
            System.err.println("Missing Server Argument: expected the server's URL as argument " + argNum);
            fail |= true;
        }
        argNum++;

        fail |= readCommandLineFileArgs(argNum, args);
        fail |= canWriteFileIsFail();
        if (fail) System.exit(1);
        new ModeTwoRunner(username, password, server);
    }

    private static void doMode1(String... args) throws IOException {
        boolean fail = false;
        int portNumber = 8090;
        int argNum = 1; // arg 1 is the mode
        if (args.length > argNum) {
            String arg = args[argNum];
            try {
                portNumber = Integer.parseInt(arg);
                if (portNumber < 1024 || portNumber > 65535) {
                    invalidPortError("" + portNumber, false);
                    fail |= true;
                }
                System.getProperties().put("server.port", portNumber);
            } catch (NumberFormatException e) {
                invalidPortError(arg, true);
                fail |= true;
            }
        }
        argNum++;

        fail |= readCommandLineFileArgs(argNum, args);
        fail |= canWriteFileIsFail();
        if (fail) System.exit(1);

        ConfigurableApplicationContext context = SpringApplication.run(WebServerApplication.class, args);
        //        context.getBean(GameService.class).init();
        System.out.println("Started Morris Client Wrapper in Mode 1: Server Based API End-Point on Port :" + portNumber);
        System.out.println("Opening Phase Command Line: " + MoveHandler.openingCommandLine);
        System.out.println("Mid/End Phase Command Line: " + MoveHandler.gameCommandLine);
    }

    private static boolean readCommandLineFileArgs(int startingArgNum, String... args) throws IOException {
        boolean fail = false;
        int argNum = startingArgNum;

        if (args.length > argNum) {
            String arg = args[argNum];
            fail |= commandLineFileIsFail(arg);
            MoveHandler.openingCommandLine = Utils.getFileAsString(arg).trim();
        } else {
            fail |= commandLineFileIsFail(Constants.OPENING_COMMAND_LINE_FILE);
            MoveHandler.openingCommandLine = Utils.getFileAsString(Constants.OPENING_COMMAND_LINE_FILE).trim();
        }
        argNum++;
        if (args.length > argNum) {
            String arg = args[argNum];
            fail |= commandLineFileIsFail(arg);
            MoveHandler.gameCommandLine = Utils.getFileAsString(arg).trim();
        } else {
            fail |= commandLineFileIsFail(Constants.GAME_COMMAND_LINE_FILE);
            MoveHandler.gameCommandLine = Utils.getFileAsString(Constants.GAME_COMMAND_LINE_FILE).trim();
        }
        return fail;
    }

    private static boolean commandLineFileIsFail(String fileName) {
        boolean fail = false;
        File f = new File(fileName);
        if (!f.exists() || f.isDirectory()) {
            System.err.println("Missing required file: " + fileName);
            fail = true;
        }
        try {
            String commandLine = Utils.getFileAsString(fileName);
            commandLine = StringUtils.trimToNull(commandLine);
            if (commandLine == null) {
                System.err.println("Error: " + fileName + " was empty");
                fail = true;
            }
            if (!commandLine.contains(Constants.MOVE_INPUT_FILE)) {
                System.err.println("Error: " + fileName + " missing required placeholder '" + Constants.MOVE_INPUT_FILE + "'");
                fail = true;
            }
            if (!commandLine.contains(Constants.MOVE_OUTPUT_FILE)) {
                System.err.println("Error: " + fileName + " missing required placeholder '" + Constants.MOVE_OUTPUT_FILE + "'");
                fail = true;
            }
        } catch (IOException e) {
            System.err.println("Error reading " + fileName + ": " + e.getMessage());
            fail = true;
        }
        return fail;
    }

    public static void invalidPortError(String requestedPort, boolean notANumber) {
        System.err.println("Invalid Port '" + requestedPort + "' specified:");
        if (notANumber) System.err.println("\tParameter 1: Server Port was not a valid number.");
        System.err.println("\tPlease specify a valid port number.  The valid port number range is 1024-65535");
    }

    private static boolean canWriteFileIsFail() {
        try {
            Utils.writeBoardState(Constants.MOVE_INPUT_FILE, "xxx", true);
        } catch (IOException e) {
            System.err.println("Insufficient file system permissions: Unable to write to files in this directory");
            return true;
        }
        return false;
    }
}
