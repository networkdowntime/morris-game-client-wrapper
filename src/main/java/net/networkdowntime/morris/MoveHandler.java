package net.networkdowntime.morris;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.networkdowntime.morris.dtos.GameState;

public class MoveHandler {
    static final Logger log = LogManager.getLogger(MoveHandler.class);
    public static String openingCommandLine = null;
    public static String gameCommandLine = null;

    public static synchronized String getNextMove(GameState gameState, int unplayedPieceCount) throws Exception {
        String boardState = gameState.currentBoardState;
        long currentTimestamp = System.currentTimeMillis();
        String inputFileName = Constants.MOVE_INPUT_FILE + "_" + currentTimestamp + ".txt";
        String outputFileName = Constants.MOVE_OUTPUT_FILE + "_" + currentTimestamp + ".txt";

        String gamePhase;
        String command;
        try {
            if (unplayedPieceCount > 0) {
                gamePhase = "Opening";
                if (openingCommandLine != null) command = openingCommandLine;
                else command = Utils.getFileAsString(Constants.OPENING_COMMAND_LINE_FILE).trim();
            } else {
                gamePhase = "Mid/End-Game";
                if (gameCommandLine != null) command = gameCommandLine;
                else command = Utils.getFileAsString(Constants.GAME_COMMAND_LINE_FILE).trim();
            }

            command = command.replace(Constants.MOVE_INPUT_FILE, inputFileName);
            command = command.replace(Constants.MOVE_OUTPUT_FILE, outputFileName);

            Utils.writeBoardState(inputFileName, boardState, false);

            log.info("Running Command: " + command);
            long startTime = System.currentTimeMillis();
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            long totalTime = System.currentTimeMillis() - startTime;

            List<String> inputLines = Utils.readInputStream(process.getInputStream());
            List<String> errorLines = Utils.readInputStream(process.getErrorStream());

            if (exitCode == 0) {
                String nextMove = Utils.getFileAsString(outputFileName).trim();
                if (nextMove != null && nextMove.length() == boardState.length()) {

                    Utils.tryDeletingFiles(inputFileName, outputFileName);
                    String inputBoardStateLabel = "Input Board State: ";
                    String outputMoveLabel = gamePhase + " Output Move: ";
                    int inputLabelLen = inputBoardStateLabel.length();
                    int outputLabelLen = outputMoveLabel.length();

                    if (inputLabelLen < outputLabelLen) inputBoardStateLabel = inputBoardStateLabel + Utils.getWhiteSpacePadding(outputLabelLen - inputLabelLen);
                    else if (inputLabelLen > outputLabelLen) outputMoveLabel = outputMoveLabel + Utils.getWhiteSpacePadding(inputLabelLen - outputLabelLen);

                    log.info(System.lineSeparator() //
                            + "Game Id: " + gameState.id + "; move #: " + gameState.numberOfMovesMade + "; Move Execution Time: " + totalTime + System.lineSeparator() //
                            + inputBoardStateLabel + boardState + System.lineSeparator()//
                            + Utils.highlightMove(outputMoveLabel, boardState, nextMove, true));

                    return nextMove;
                } else {
                    log.error("The move output file '" + outputFileName + "' did not contain a valid board state string.");
                    if (nextMove != null) log.error("The file was empty.");
                    if (nextMove.length() != boardState.length()) {
                        log.error("The move was expected to be " + boardState.length() + " characters but was " + nextMove.length() + " characters.");
                    }
                }
            } else {
                log.error("Command had a non-zero exit value of: " + exitCode);
            }

            log.error("Standard Out from command execution was " + inputLines.size() + " lines");
            for (String s : inputLines) {
                log.error(s);
            }

            log.error("Standard Error from command execution was " + errorLines.size() + " lines");
            for (String s : errorLines) {
                log.error(s);
            }

        } catch (Exception e) {
            log.error("Error processing move request:");
            log.error(e);
        }

        throw new Exception("Unable to get the next move");
    }
}
