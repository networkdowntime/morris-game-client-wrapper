package net.networkdowntime.morris;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.CookieStore;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.networkdowntime.morris.dtos.GameState;
import net.networkdowntime.morris.dtos.JoinGameRequest;
import net.networkdowntime.morris.dtos.LoginRequest;
import net.networkdowntime.morris.dtos.MoveRequest;
import net.networkdowntime.morris.dtos.NewGameRequest;

/**
 * Mode 2 - Client Poll Mode:
    Description: 
        Logged Out State: Wrapper POSTs a login request on startup, tracks the sessionId or cookie, them moves to the Game Polling State.
        Game Polling State: Requests /api/game/myActiveGames.  If has Active Games move to Game Playing State else move to Open Game State
        Game Playing State:
            For Each Active Game: 
                Is Player’s Turn: Propose next move based on the GameState
                Not Player’s Turn: Poll game activeGames every 20 seconds until player’s turn
        Open Game State: Request /api/game/myOpenGames; Set openGameCount = # of open games
            If Has My Open Games: Sleep 60-120 seconds; move to Open Game State
            If openGameCount Decreased: One of my open games moved to the Active phase, move to Game Playing State
            If openGameCount == 0: Request /api/game/availableOpenGames
                If Has Available Open Games: Move to Join Game State 
                If Available Open Games == 0: Move to New Game State
        Join Game State: POST request to join the first Available Open Game.  On success move to Game Playing State, on failure move to Open Game State
        New Game State: POST request to create a new game; Move to Open Game State
        
 * @author rwiles
 *
 */
public class ModeTwoRunner {
    static final Logger log = LogManager.getLogger(ModeTwoRunner.class);

    private static final String LOGIN_ENDPOINT = "/api/user/login";
    private static final String GAME_BOARDS_ENDPOINT = "/api/game/availableGameBoards";
    private static final String ALL_OPEN_GAMES_ENDPOINT = "/api/game/availableOpenGames";
    private static final String MY_OPEN_GAMES_ENDPOINT = "/api/game/myOpenGames";
    private static final String MY_ACTIVE_GAMES_ENDPOINT = "/api/game/myActiveGames";
    private static final String NEW_GAME_ENDPOINT = "/api/game/newGame";
    private static final String JOIN_GAME_ENDPOINT = "/api/game/joinOpenGame";
    private static final String APPLY_MOVE_ENDPOINT = "/api/game/applyMove";

    enum State {
        LoggedOut, GamePolling, GamePlaying, OpenGame, JoinGame, NewGame
    };

    ObjectMapper objectMapper = new ObjectMapper();

    CookieStore cookieStore = new BasicCookieStore();
    String server;
    State state = State.LoggedOut;
    List<GameState> activeGames = new ArrayList<GameState>();
    List<GameState> allOpenGames = new ArrayList<GameState>();
    List<GameState> myOpenGames = new ArrayList<GameState>();
    int myOpenGameCount = 0;
    String username;

    public ModeTwoRunner(String username, String password, String server) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.server = server;
        this.username = username;

        do {
            switch (state) {
                case LoggedOut:
                    state = doLogin(password);
                    break;
                case GamePolling:
                    state = doGamePolling();
                    break;
                case GamePlaying:
                    state = doGamePlaying();
                    break;
                case JoinGame:
                    state = doJoinGame();
                    break;
                case NewGame:
                    state = doNewGame();
                    break;
                case OpenGame:
                    state = doOpenGame();
                    break;
                default:
                    break;
            }
            try {
                synchronized (this) {
                    this.wait(1 * 1000);
                }
            } catch (InterruptedException e) {}
        } while (true);
    }

    private boolean isAutomated(GameState gs) {
        return (gs.isPlayerOnesTurn && username.equals(gs.playerOneUsername) && gs.playerOneIsAutomated && StringUtils.isEmpty(gs.playerOneAiEndpoint)) // 
                || (!gs.isPlayerOnesTurn && username.equals(gs.playerTwoUsername) && gs.playerTwoIsAutomated && StringUtils.isEmpty(gs.playerTwoAiEndpoint));
    }

    private boolean isPlayerOne(GameState gs) {
        return (gs.isPlayerOnesTurn && username.equals(gs.playerOneUsername) && gs.playerOneIsAutomated && StringUtils.isEmpty(gs.playerOneAiEndpoint));
    }

    private boolean isPlayerTwo(GameState gs) {
        return (!gs.isPlayerOnesTurn && username.equals(gs.playerTwoUsername) && gs.playerTwoIsAutomated && StringUtils.isEmpty(gs.playerTwoAiEndpoint));
    }

    // Logged Out State: Wrapper POSTs a login request on startup, tracks the sessionId or cookie, them moves to the Game Polling State.
    private State doLogin(String password) {
        int failCount = 0;
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.username = username;
        loginRequest.password = password;

        do {
            try {
                HttpClientUtils.doPostJsonReturnText(server + LOGIN_ENDPOINT, loginRequest, cookieStore);
                return State.GamePolling;
            } catch (Exception e) {
                failCount++;
                this.state = State.LoggedOut;

                log.info("Got an error logging in.");
                log.debug(e.getMessage());
                if (log.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }

            try {
                synchronized (this) {
                    if (failCount < 5) {
                        System.out.println("Login for user " + username + " failed. Waiting for 10 seconds before trying again.");
                        this.wait(10000);
                    } else {
                        System.out.println("Login for user " + username + " failed 5 time. Waiting for 5 minutes before trying again.");
                        this.wait(5 * 60 * 1000);
                    }
                }
            } catch (InterruptedException e) {}
        } while (this.state == State.LoggedOut);
        return State.LoggedOut;
    }

    // Game Polling State: Requests /api/game/myActiveGames.  If has Active Games move to Game Playing State else move to Open Game State
    private State doGamePolling() {
        System.out.println("Entering Game Polling State:");

        try {
            String json = HttpClientUtils.doGet(server + MY_ACTIVE_GAMES_ENDPOINT, cookieStore, ContentType.APPLICATION_JSON);
            GameState[] games = objectMapper.readValue(json, GameState[].class);

            activeGames.clear();
            for (GameState gs : games)
                if (gs != null && isAutomated(gs)) activeGames.add(gs);

            if (activeGames.isEmpty()) return State.OpenGame;
            else return State.GamePlaying;

        } catch (Exception e) {
            log.info("Got an error getting active games.  Going to try logging in again.");
            log.debug(e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return State.LoggedOut;
        }
    }

    // Game Playing State: For Each Active Game: 
    //        Is Player’s Turn: Propose next move based on the GameState
    //        Not Player’s Turn: Poll game activeGames every 20 seconds until player’s turn
    private State doGamePlaying() {
        System.out.println("Entering Game Playing State:");
        try {
            do {
                String json = HttpClientUtils.doGet(server + MY_ACTIVE_GAMES_ENDPOINT, cookieStore, ContentType.APPLICATION_JSON);
                GameState[] games = objectMapper.readValue(json, GameState[].class);

                activeGames.clear();
                for (GameState gs : games)
                    if (gs != null && isAutomated(gs)) activeGames.add(gs);

                System.out.println("\tI have " + activeGames.size() + " games");

                boolean notMyTurn = false;
                for (GameState gameState : activeGames) {
                    boolean isPlayerOne = isPlayerOne(gameState);
                    boolean isPlayerTwo = isPlayerTwo(gameState);
                    boolean isMyTurn = (gameState.isPlayerOnesTurn && isPlayerOne) // 
                            || (!gameState.isPlayerOnesTurn && isPlayerTwo);

                    String opponent = username.equals(gameState.playerOneUsername) && gameState.playerOneIsAutomated ? gameState.playerTwoUsername : gameState.playerOneUsername;

                    System.out.println("\tGame Id " + gameState.id + "; " + ((isMyTurn) ? "It's my turn" : "It's not my turn, waiting on " + opponent));

                    // is it my turn?
                    if (isMyTurn) {
                        notMyTurn = true;
                        String nextMove = null;

                        System.out.println("        " + gameState.currentBoardState + "; is player 1's turn: " + gameState.isPlayerOnesTurn + "; player 1 unplayed pieces: " + gameState.playerOneUnplayedPieceCount + "; player 2 unplayed pieces: "
                                + gameState.playerTwoUnplayedPieceCount);

                        if (gameState.isPlayerOnesTurn) {
                            nextMove = MoveHandler.getNextMove(gameState, gameState.playerOneUnplayedPieceCount);
                        } else { // I'm Player 2, need to swap the board state
                            gameState.swapPlayers('W', 'B');
                            nextMove = MoveHandler.getNextMove(gameState, gameState.playerOneUnplayedPieceCount);
                            gameState.swapPlayers('W', 'B');
                            nextMove = Utils.swapBoardState(nextMove, 'W', 'B');
                        }

                        MoveRequest moveRequest = new MoveRequest();
                        moveRequest.gameId = gameState.id;
                        moveRequest.playerName = username;
                        moveRequest.boardState = nextMove;

                        try {
                            json = HttpClientUtils.doPostJsonReturnJson(server + APPLY_MOVE_ENDPOINT, moveRequest, cookieStore);
                            GameState moveGameState = objectMapper.readValue(json, GameState.class);
                            System.out.println(Utils.highlightMove("    Game Id:" + gameState.id + "; Move #" + moveGameState.numberOfMovesMade, gameState.currentBoardState, moveGameState.currentBoardState, gameState.isPlayerOnesTurn));
                        } catch (Exception e) {
                            log.debug(e.getMessage());
                            if (log.isDebugEnabled()) e.printStackTrace();
                            System.out.println(Utils.highlightMove("    Move Rejected By Server: Game Id:" + gameState.id, gameState.currentBoardState, nextMove, gameState.isPlayerOnesTurn));
                        }
                    }
                }
                if (!notMyTurn && activeGames.size() > 0) {
                    try {
                        synchronized (this) {
                            System.out.println("I have " + activeGames.size() + " active games, but it's not my turn. Waiting for 20s");
                            this.wait(20 * 1000);
                        }
                    } catch (InterruptedException e) {}
                }
                try {
                    synchronized (this) {
                        System.out.println("Game Playing loop slow down 2s");
                        this.wait(2 * 1000);
                    }
                } catch (InterruptedException e) {}
            } while (!activeGames.isEmpty());
        } catch (Exception e) {
            log.info("Got an error getting active games.  Going to try logging in again.");
            log.debug(e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return State.LoggedOut;
        }
        return State.GamePolling;
    }

    // Join Game State: POST request to join the first Available Open Game.  
    //      On success move to Game Playing State
    //      On failure move to Open Game State
    private State doJoinGame() {
        System.out.println("Entering Join Game State:");

        if (allOpenGames.isEmpty()) return State.OpenGame;
        for (GameState gameState : allOpenGames) {
            try {
                JoinGameRequest jgr = new JoinGameRequest();
                jgr.gameId = gameState.id;
                jgr.playerName = username;
                jgr.isAutomated = true;

                String json = HttpClientUtils.doPostJsonReturnJson(server + JOIN_GAME_ENDPOINT, jgr, cookieStore);
                GameState joinedGameState = objectMapper.readValue(json, GameState.class);
                log.info("Joined game with id: " + joinedGameState);
                return State.GamePlaying;

            } catch (Exception e) {
                log.info("Could not join game id " + gameState.id + ".  Maybe somebody else beat me to it");
                log.debug(e.getMessage());
            }
        }
        return State.OpenGame;
    }

    // New Game State: POST request to create a new game; Move to Open Game State
    private State doNewGame() {
        System.out.println("Entering New Game State:");

        try {
            String json = HttpClientUtils.doGet(server + GAME_BOARDS_ENDPOINT, cookieStore, ContentType.APPLICATION_JSON);
            String[] boards = objectMapper.readValue(json, String[].class);
            String board = boards[0];

            NewGameRequest ngr = new NewGameRequest();
            ngr.gameBoardName = board;
            ngr.isPlayerOne = Math.round(Math.random()) == 0 ? false : true;
            ngr.playerName = username;
            ngr.isAutomated = true;

            json = HttpClientUtils.doPostJsonReturnJson(server + NEW_GAME_ENDPOINT, ngr, cookieStore);
            GameState gameState = objectMapper.readValue(json, GameState.class);
            System.out.println("Created a new game with id: " + gameState.id + "; I'm player " + (ngr.isPlayerOne ? "1" : "2"));
            return State.GamePolling;

        } catch (Exception e) {
            log.info("Got an error creating a new game.  Going to try logging in again.");
            log.debug(e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return State.LoggedOut;
        }
    }

    // Open Game State: Request /api/game/myOpenGames; Set openGameCount = # of open games
    //    If Has My Open Games: Sleep 60-120 seconds; move to Open Game State
    //    If openGameCount Decreased: One of my open games moved to the Active phase, move to Game Playing State
    //    If openGameCount == 0: Request /api/game/availableOpenGames
    //        If Has Available Open Games: Move to Join Game State 
    //        If Available Open Games == 0: Move to New Game State
    private State doOpenGame() {
        System.out.println("Entering Open Game State:");

        try {
            do {
                String json = HttpClientUtils.doGet(server + MY_OPEN_GAMES_ENDPOINT, cookieStore, ContentType.APPLICATION_JSON);
                GameState[] games = objectMapper.readValue(json, GameState[].class);

                myOpenGames.clear();
                for (GameState gs : games)
                    if (gs != null && isAutomated(gs)) myOpenGames.add(gs);

                if (myOpenGames.size() < myOpenGameCount) {
                    myOpenGameCount = myOpenGames.size();
                    return State.GamePlaying;
                }
                myOpenGameCount = myOpenGames.size();

                if (myOpenGames.isEmpty()) {
                    json = HttpClientUtils.doGet(server + ALL_OPEN_GAMES_ENDPOINT, cookieStore, ContentType.APPLICATION_JSON);
                    games = objectMapper.readValue(json, GameState[].class);

                    allOpenGames.clear();
                    for (GameState gs : games)
                        if (gs != null && !isPlayerOne(gs) && !isPlayerTwo(gs)) allOpenGames.add(gs);

                    if (allOpenGames.size() < 3) return State.NewGame;
                    else return State.JoinGame;
                }

                try {
                    synchronized (this) {
                        System.out.println("I have " + myOpenGameCount + " open games, waiting for another player to join. Waiting for 2 minutes");
                        this.wait(120 * 1000);
                    }
                } catch (InterruptedException e) {}
            } while (true);
        } catch (Exception e) {
            log.info("Got an error getting my open games.  Going to try logging in again.");
            log.debug(e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return State.LoggedOut;
        }
    }

}
