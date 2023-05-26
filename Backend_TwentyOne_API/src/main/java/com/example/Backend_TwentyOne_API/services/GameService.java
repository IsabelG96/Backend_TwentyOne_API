package com.example.Backend_TwentyOne_API.services;

import com.example.Backend_TwentyOne_API.models.Game;
import com.example.Backend_TwentyOne_API.models.GameType;
import com.example.Backend_TwentyOne_API.models.Player;
import com.example.Backend_TwentyOne_API.models.Reply;
import com.example.Backend_TwentyOne_API.repositories.GameRepository;
import com.example.Backend_TwentyOne_API.repositories.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class GameService {

//    Need random object to 'flip coin' to decide who starts game
    Random random = new Random();

    @Autowired
    GameRepository gameRepository;

    @Autowired
    PlayerService playerService;

    @Autowired
    PlayerRepository playerRepository;

//    DEFAULT CONSTRUCTOR
    public GameService(){
    }
    public List<Game> getAllGames() {
        return gameRepository.findAll();
    }

    public Optional<Game> getGameById(Long id) {
        return gameRepository.findById(id);
    }


    public ResponseEntity<Reply> createNewGame(long playerId, String gameType) {

//        Get player by playerId
        Optional<Player> player = playerService.getPlayerById(playerId);

//        Initialise responseEntity
        ResponseEntity<Reply> responseEntity;

//        Check if player exists
        if(!player.isPresent()){
             responseEntity = new ResponseEntity(null, HttpStatus.NOT_FOUND);
        }
//        Create game depending on gameType input
        else if (gameType.equalsIgnoreCase("Easy")) {
            GameType newGameType = GameType.EASY;
            responseEntity = createGameByType(newGameType, player);
        } else if (gameType.equalsIgnoreCase("difficult")) {
            GameType newGameType = GameType.DIFFICULT;
            responseEntity = createGameByType(newGameType, player);
        } else if (gameType.equalsIgnoreCase("multiplayer")) {
            GameType newGameType = GameType.MULTIPLAYER;
            responseEntity = createGameByType(newGameType, player);
        } else {
            responseEntity  = new  ResponseEntity<>(null, HttpStatus.NOT_ACCEPTABLE);
        }
        return responseEntity;

    }

    public ResponseEntity<Reply> startNewGame(Long gameId) {

//        Get game by id
        Optional <Game> optionalGame = getGameById(gameId);

//        initialise empty responseEntity
        ResponseEntity<Reply> responseEntity;

//        Check if game exists
        if (optionalGame.isEmpty()){
            responseEntity = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
//        check if game has already been started
        else if (optionalGame.get().getHasStarted()){
            Reply reply = startGameAlreadyStarted(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }
//        Check if multiplayer game has enough players to begin
        else if (optionalGame.get().getGameType().equals(GameType.MULTIPLAYER)
        && optionalGame.get().getPlayers().size() < 2){
            Reply reply = startGameMultiplayerNotEnoughPlayers(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }
//        Start game depending on gameType
        else if (optionalGame.get().getGameType().equals(GameType.MULTIPLAYER)){
            Reply reply = startNewGameMultiplayer(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.ACCEPTED);
        } else {
            Reply reply = startNewGameSinglePlayer(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.ACCEPTED);
        } return responseEntity;
    }


    public Reply startNewGameSinglePlayer(Long gameId){

        Game game = getGameById(gameId).get();

//        start game
        game.setHasStarted(true);
        gameRepository.save(game);

//        flip coin to decide who starts
        int whoToStart = random.nextInt(0,2);

        String message;

//        if computer to start
        if (whoToStart == 0) {
            int computerTurn;
            if (game.getGameType().equals(GameType.DIFFICULT)){
                computerTurn = computerTurnDifficult(game);
            } else { computerTurn = computerTurnEasy(game);}
            message = "Computer starts. Computer plays " + computerTurn + ". Player "
                    + game.getCurrentPlayerId() + ", " + playerService.getPlayerNameByGame(game)
                    + ",git Your turn!";
        }

//        if player to start
        else{
            message ="Player " + game.getCurrentPlayerId() + ", " + playerService.getPlayerNameByGame(game) + " to start. Your turn.";
        }
        gameRepository.save(game);

        return new Reply(
                game.getCurrentTotal(),
                false,
                message
        );
    }

    public Reply startNewGameMultiplayer(Long gameId) {
//        get game by id
        Game game = getGameById(gameId).get();

//        start game
        game.setHasStarted(true);
        gameRepository.save(game);

//        flip coin to decide who starts
        int whoToStart = random.nextInt(0,game.getPlayers().size());
        Player player = game.getPlayers().get(whoToStart);
        Long firstPlayerId = player.getId();
        game.setCurrentPlayerId(firstPlayerId);
        gameRepository.save(game);
        String firstPlayerName = player.getName();

//       identify starting player and personalise message
        String message = "Player " + firstPlayerId + ", " + firstPlayerName + ", to start!";
        return new Reply(
                game.getCurrentTotal(),
                false,
                message
        );
    }

//    used in startGame method to return error if game has already started
    public Reply startGameAlreadyStarted(Long gameId){
        Game game = getGameById(gameId).get();
        int currentTotal = game.getCurrentTotal();
        Boolean complete = game.getComplete();
        return new Reply(
                currentTotal,
                complete,
                "Game with id " + game.getId() + " has already been started"
        );
    }

//    METHOD TO PROCESS TURN
    public ResponseEntity<Reply> submitTurn(Long gameId, Long playerId, int guess) {

//        Get game and player by ids
        Optional<Game> game = getGameById(gameId);
        Optional<Player> player = playerService.getPlayerById(playerId);

//        initialise empty responseEntity
        ResponseEntity<Reply> responseEntity;

//        check game exists
        if (game.isEmpty()){
            responseEntity = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }

//        check player exists
        else if (player.isEmpty()){
            responseEntity = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }

//      check game has started
        else if (!game.get().getHasStarted()){
            Reply reply = gameNotStarted(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }

//        check game has not finished
        else if (game.get().getComplete()){
            Reply reply = gameAlreadyComplete(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }
//        check player who is submitting turn is correct player
        else if (game.get().getCurrentPlayerId()!=playerId){
            Reply reply = wrongPlayer(gameId, playerId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }

//        check guess is 1, 2, or 3
        else if (!((guess < 4) && (guess > 0))){
            Reply reply = invalidGuess(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }

//        process turn if multiplayer
        else if(game.get().getGameType().equals(GameType.MULTIPLAYER)){
            Reply reply = processTurnMultiplayer(gameId, guess);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.OK);
        }

//        process turn if single player
        else{
            Reply reply = processTurnSinglePlayer(gameId, guess);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.OK);
        }
        return  responseEntity;
      }


//      used in submitTurn method to return error if game has not yet started
    private Reply gameNotStarted(Long gameId) {
        Game game = getGameById(gameId).get();
        return new Reply(
                0,
                false,
                "Game with id " + game.getId() + " has not been started"
        );

    }

//    used in submitTurn to process turn in single player game
    public Reply processTurnSinglePlayer (Long gameId, int guess){

        // find the correct game and player
        Game game = gameRepository.findById(gameId).get();

        Player player = game.getLeadPlayer();

        // increment total by user input
        game.setCurrentTotal(game.getCurrentTotal()+ guess);
        gameRepository.save(game);

        // Check is below 21
        if (game.getCurrentTotal()> 20){
            game.setComplete(true);
            gameRepository.save(game);
            player.incrementGamesLost();
            playerRepository.save(player);

            return new Reply(game.getCurrentTotal(),game.getComplete(),"Game Over! You lose :(");
        }

        // Computer guess depending on whether easy or difficult mode
        int computerTurn;
        if (game.getGameType().equals(GameType.DIFFICULT)){
            computerTurn = computerTurnDifficult(game);
        }
        else { computerTurn = computerTurnEasy(game);}
        gameRepository.save(game);

//        check if computer has now lost
        if (game.getCurrentTotal()>20){
            game.setComplete(true);
            gameRepository.save(game);
            return new Reply(game.getCurrentTotal(), game.getComplete(), "Game Over! You win :D");
        }

//        return to player to submit next turn
        else {
            return new Reply(game.getCurrentTotal(), false, "Computer played " + computerTurn + "! Your move...");
        }

    }

//      used in submitTurn method to process turn in multiplayer game
    public Reply processTurnMultiplayer(Long gameId, int guess) {

        // find the correct game
        Game game = gameRepository.findById(gameId).get();

//        find list of players
        List<Player> playerList = game.getPlayers();

//        find current player
        Long currentPlayerId = game.getCurrentPlayerId();
        Player currentPlayer = playerService.getPlayerById(currentPlayerId).get();

//        increment total by user input
        game.setCurrentTotal(game.getCurrentTotal()+ guess);
        gameRepository.save(game);

//        Check is below 21

        if (game.getCurrentTotal()> 20) {
            game.setComplete(true);
            gameRepository.save(game);
            currentPlayer.incrementGamesLost();
            playerRepository.save(currentPlayer);
            return new Reply(
                    game.getCurrentTotal(),
                    game.getComplete(),
                    "Game Over! Player " + game.getCurrentPlayerId() + ", "
                            + playerService.getPlayerNameById(game.getCurrentPlayerId())  +" loses :("
            );
        }
//        if not, increment player turn to the next person and return
        else {
            Long nextPlayerId = idOfNextPlayerToGuess(playerList, currentPlayerId);
            game.setCurrentPlayerId(nextPlayerId);
            gameRepository.save(game);
            return new Reply(game.getCurrentTotal(),
                    false,
                    "It is player " + game.getCurrentPlayerId() + ", " + playerService.getPlayerNameById(game.getCurrentPlayerId())  + "'s turn"
            );
        }
    }


//    logic for strategy for computer in hard mode single player (go to multiple of 4 if you can)
    public int computerTurnDifficult(Game game) {
        int computerTurn;
        if (game.getCurrentTotal() % 4 != 0) {
            computerTurn = 4 - game.getCurrentTotal() % 4;
        } else {
            computerTurn = random.nextInt(1, 4);
        }
        game.incrementCurrentTotal(computerTurn);
        return computerTurn;
    }

//    picks random number to play
    public int computerTurnEasy(Game game) {
        int computerTurn = random.nextInt(1,4);
        game.incrementCurrentTotal(computerTurn);
        return computerTurn;
    }

//    used in processTurnMultiplayer to cycle through list of players to get next player to have turn
    public Long idOfNextPlayerToGuess(List<Player> playerList, Long currentPlayerId){

//        get player by playerId
        Player currentPlayer = playerService.getPlayerById(currentPlayerId).get();

//        get index of player in list by currentPlayerId
        int currentPlayerIndex = playerList.indexOf(currentPlayer);

//        increment by 1, looping if needed
        int nextPlayerIndex = (currentPlayerIndex + 1) % playerList.size();

//        set next player to have turn and return their id
        Player nextPlayer = playerList.get(nextPlayerIndex);
        Long nextPlayerId = nextPlayer.getId();
        return nextPlayerId;
    }


//    used in submitTurn method in case input is not 1,2, or 3
    public Reply invalidGuess(Long gameId) {
        Game game = getGameById(gameId).get();
        int currentTotal = game.getCurrentTotal();
        Boolean complete = game.getComplete();
        return new Reply(
                currentTotal,
                complete,
                "Invalid turn, please input either 1, 2, or 3"
        );
    }

//    METHOD TO ADD PLAYER TO GAME BEFORE IT STARTS
    public ResponseEntity<Reply> addPlayerToGame(Long playerId, Long gameId) {

//        Get the player by the playerId,
        Optional<Player> player = playerService.getPlayerById(playerId);

//        get game by gameId,
        Optional<Game> game = getGameById(gameId);

//        create empty responseEntity
        ResponseEntity<Reply> responseEntity;

//        Check the player exists
        if(player.isEmpty()){
            responseEntity = new ResponseEntity<>(null,HttpStatus.NOT_FOUND);
        }
//        Check game exists
        else if(game.isEmpty()){
            responseEntity = new ResponseEntity<>(null,HttpStatus.NOT_FOUND);
        }
//        Check the players not already in the game
        else if (game.get().getPlayers().contains(player.get())){
            Reply reply = addPlayerToGameAlreadyContains(gameId, playerId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }
//        Check the game hasn't started
        else if(game.get().getHasStarted()){
            Reply reply = addPlayerToGameAlreadyStarted(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }
//        Check if game is multiplayer
        else if (!game.get().getGameType().equals(GameType.MULTIPLAYER)){
            Reply reply = addPlayerToWrongGameType(gameId);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.NOT_ACCEPTABLE);
        }
//        Start game
        else{
            Game actualGame = game.get();
            actualGame.addPlayer(player.get());
            gameRepository.save(actualGame);
            String message = "Player " + player.get().getId() + ", " + player.get().getName() + ", has been added to game " + actualGame.getId() + ".";
            Reply reply = new Reply(actualGame.getCurrentTotal(), actualGame.getComplete(), message);
            responseEntity = new ResponseEntity<>(reply, HttpStatus.OK);
        }
//        return response entity
        return responseEntity;
    }


//    used in startGame method in case multiplayer game has only 1 player
    public Reply startGameMultiplayerNotEnoughPlayers(Long gameId) {
        Game game = getGameById(gameId).get();
        String playerName = game.getLeadPlayer().getName();
        return new Reply(
                0,
                false,
                 "Only " + playerName + " has joined the game, not enough players to begin"
        );
    }

//    used in submitTurn in case player tries to play when it is not their turn
    public Reply wrongPlayer(Long gameId, Long playerId) {
        Game game = getGameById(gameId).get();
        Long currentPlayerId = game.getCurrentPlayerId();
        int gameState = game.getCurrentTotal();
        return new Reply(
                gameState,
                false,
                "Not your turn, player " + currentPlayerId + " is next."
        );
    }

//    used in addPlayerToGame method in case player is already in game
    public Reply addPlayerToGameAlreadyContains(Long gameId, Long playerId) {
        Game game = getGameById(gameId).get();
        Player player = playerService.getPlayerById(playerId).get();
        String message = "You're already here, "  + player.getName() + "!";
        return new Reply(game.getCurrentTotal(), game.getComplete(), message);
    }

//    used in addPlayerToGame method in case game has already began
    public Reply addPlayerToGameAlreadyStarted(Long gameId) {
        Game game = getGameById(gameId).get();
        String message = "Game " + game.getId() + " has already started. Cannot add anymore players";
        return new Reply(game.getCurrentTotal(), game.getComplete(), message);
    }

//    used in addPlayerToGame method in case game is single player
    public Reply addPlayerToWrongGameType(Long gameId) {
        Game game = getGameById(gameId).get();
        String message = "Game " + game.getId() + " is not multiplayer. Cannot add players";
        return new Reply(game.getCurrentTotal(), game.getComplete(), message);
    }

//    used in submitTurn method in case player tries to submit turn to game that has finished
    public Reply gameAlreadyComplete(Long gameId) {
        Game game = getGameById(gameId).get();
        String message = "Game " + game.getId() + " is finished. Cannot play anymore.";
        return new Reply(game.getCurrentTotal(), game.getComplete(), message);
    }

//    Used to delete game
    public void deleteGame(long gameId) {
        Game game = gameRepository.findById(gameId).get();

//        cycle through player in game, remove them before can delete game
        List<Player> playerList = game.getPlayers();
        for(Player player : playerList){
            player.removeGame(game);
            playerRepository.save(player);
        }
        gameRepository.deleteById(gameId);

    }

    public ResponseEntity<String> removePlayerFromGame(Long gameId, Long playerId) {
//        get the target game by id
        Optional<Game> optionalGame = getGameById(gameId);
//        get the target player by id
        Optional<Player> optionalPlayer = playerService.getPlayerById(playerId);
//        new lead player
        Player leadPlayer;
        Game game;
//        check if game is present
        if (optionalGame.isEmpty()){
            return new ResponseEntity<String>("Game not found",HttpStatus.NOT_FOUND);
        }
//        get the lead player
        else{
            leadPlayer = optionalGame.get().getLeadPlayer();
            game = optionalGame.get();
        }
//        check if the player is present
        if (optionalPlayer.isEmpty()){
            return new ResponseEntity<String>("Player not found",HttpStatus.NOT_FOUND);
        }
//        check if the game has already started
        else if (game.getHasStarted()){
            return new ResponseEntity<String>("Player cannot be removed from game that has already begun!",
                    HttpStatus.NOT_ACCEPTABLE);
        }
//        check if the player is not in the game
        else if (!game.getPlayers().contains(optionalPlayer.get())){
            return new ResponseEntity<>("Player not in this game", HttpStatus.NOT_ACCEPTABLE);
        }
//        check if the player is lead player
        else if (leadPlayer.equals(optionalPlayer.get())){
            return new ResponseEntity<>("Lead player cannot be removed from game",HttpStatus.NOT_ACCEPTABLE);
        }
//        remove player from game
        else {
            Player player = optionalPlayer.get();
            game.removePlayer(player);
            gameRepository.save(game);
            return new ResponseEntity<>("Player successfully removed from game",HttpStatus.OK);
        }
    }

//        used in createGame method to create game depending on inputted gameType
    public ResponseEntity<Reply> createGameByType(GameType gameType, Optional<Player> player){
        Game game = new Game(player.get(), gameType);
        gameRepository.save(game);
        Reply reply = new Reply(0,
                false,
                "Create new game with id " + game.getId() + " with lead player " + player.get().getName());
        return new ResponseEntity<>(reply, HttpStatus.CREATED);
    }

}
