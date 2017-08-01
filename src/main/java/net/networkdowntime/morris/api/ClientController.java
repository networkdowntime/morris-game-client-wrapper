package net.networkdowntime.morris.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import net.networkdowntime.morris.MoveHandler;
import net.networkdowntime.morris.dtos.GameState;

@RestController
@RequestMapping("api/clientWrapper")
public class ClientController {
    static final Logger log = LogManager.getLogger(ClientController.class);

    @RequestMapping(value = "/processMove", //
            method = RequestMethod.POST, //
            consumes = { "application/json;charset=UTF-8" }, //
            produces = { "text/plain;charset=UTF-8" })
    public ResponseEntity<String> applyMove(@RequestBody GameState gameState) {
        try {
            String nextMove = MoveHandler.getNextMove(gameState, gameState.playerOneUnplayedPieceCount);
            return new ResponseEntity<String>(nextMove, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}