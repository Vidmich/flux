/*
 * Copyright 2007-2013 the original author or authors.
 *
 * This file is part of Flux Chess.
 *
 * Flux Chess is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flux Chess is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Flux Chess.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.fluxchess.flux.board;

import com.fluxchess.flux.Search;
import com.fluxchess.flux.move.IntCastling;
import com.fluxchess.flux.move.IntMove;
import com.fluxchess.flux.table.RepetitionTable;
import com.fluxchess.jcpi.models.*;

import java.util.Random;

public final class Hex88Board {

  /**
   * The size of the 0x88 board
   */
  public static final int BOARDSIZE = 128;

  // The size of the history stack
  private static final int STACKSIZE = Search.MAX_MOVES;

  // Game phase thresholds
  private static final int GAMEPHASE_OPENING_VALUE =
    IntChessman.VALUE_KING
      + 1 * IntChessman.VALUE_QUEEN
      + 2 * IntChessman.VALUE_ROOK
      + 2 * IntChessman.VALUE_BISHOP
      + 2 * IntChessman.VALUE_KNIGHT
      + 8 * IntChessman.VALUE_PAWN;
  private static final int GAMEPHASE_ENDGAME_VALUE =
    IntChessman.VALUE_KING
      + 1 * IntChessman.VALUE_QUEEN
      + 1 * IntChessman.VALUE_ROOK;
  private static final int GAMEPHASE_INTERVAL = GAMEPHASE_OPENING_VALUE - GAMEPHASE_ENDGAME_VALUE;
  private static final int GAMEPHASE_ENDGAME_COUNT = 2;

  private static final Random random = new Random(0);

  // The zobrist keys
  private static final long zobristActiveColor;
  private static final long[][][] zobristChessman = new long[IntChessman.CHESSMAN_VALUE_SIZE][IntColor.ARRAY_DIMENSION][BOARDSIZE];
  private static final long[] zobristCastling = new long[IntCastling.ARRAY_DIMENSION];
  private static final long[] zobristEnPassant = new long[BOARDSIZE];

  //## BEGIN 0x88 Board Representation
  public final int[] board = new int[BOARDSIZE];
  //## ENDOF 0x88 Board Representation

  // The chessman lists.
  public final PositionList[] pawnList = new PositionList[IntColor.ARRAY_DIMENSION];
  public final PositionList[] knightList = new PositionList[IntColor.ARRAY_DIMENSION];
  public final PositionList[] bishopList = new PositionList[IntColor.ARRAY_DIMENSION];
  public final PositionList[] rookList = new PositionList[IntColor.ARRAY_DIMENSION];
  public final PositionList[] queenList = new PositionList[IntColor.ARRAY_DIMENSION];
  public final PositionList[] kingList = new PositionList[IntColor.ARRAY_DIMENSION];

  // Board stack
  private final Hex88BoardStackEntry[] stack = new Hex88BoardStackEntry[STACKSIZE];
  private int stackSize = 0;

  // Zobrist code
  public long zobristCode = 0;

  // Pawn zobrist code
  public long pawnZobristCode = 0;

  // En Passant square
  public int enPassantSquare = IntPosition.NOPOSITION;

  // Castling
  public int castling;
  private final int[] castlingHistory = new int[STACKSIZE];
  private int castlingHistorySize = 0;

  // Capture
  public int captureSquare = IntPosition.NOPOSITION;
  private final int[] captureHistory = new int[STACKSIZE];
  private int captureHistorySize = 0;

  // Half move clock
  public int halfMoveClock = 0;

  // The half move number
  private int halfMoveNumber;

  // The active color
  public int activeColor = IntColor.WHITE;

  // Material values of all pieces (pawns, knights, bishops, rooks, queens, king)
  public final int[] materialValueAll = new int[IntColor.ARRAY_DIMENSION];

  // Material counters of minor and major pieces (knights, bishops, rooks, queens)
  public final int[] materialCount = new int[IntColor.ARRAY_DIMENSION];

  // Material counters of all pieces without the king (pawns, knights, bishops, rooks, queens)
  public final int[] materialCountAll = new int[IntColor.ARRAY_DIMENSION];

  // Our repetition table
  private final RepetitionTable repetitionTable;

  // Attack
  private final Attack[][] attackHistory = new Attack[STACKSIZE + 1][IntColor.ARRAY_DIMENSION];
  private int attackHistorySize = 0;
  private final Attack tempAttack = new Attack();

  // Initialize the zobrist keys
  static {
    zobristActiveColor = Math.abs(random.nextLong());

    for (int chessman : IntChessman.values) {
      for (int color : IntColor.values) {
        for (int i = 0; i < BOARDSIZE; i++) {
          zobristChessman[chessman][color][i] = Math.abs(random.nextLong());
        }
      }
    }

    zobristCastling[IntCastling.WHITE_KINGSIDE] = Math.abs(random.nextLong());
    zobristCastling[IntCastling.WHITE_QUEENSIDE] = Math.abs(random.nextLong());
    zobristCastling[IntCastling.BLACK_KINGSIDE] = Math.abs(random.nextLong());
    zobristCastling[IntCastling.BLACK_QUEENSIDE] = Math.abs(random.nextLong());
    zobristCastling[IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE] = zobristCastling[IntCastling.WHITE_KINGSIDE] ^ zobristCastling[IntCastling.WHITE_QUEENSIDE];
    zobristCastling[IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE] = zobristCastling[IntCastling.BLACK_KINGSIDE] ^ zobristCastling[IntCastling.BLACK_QUEENSIDE];

    for (int i = 0; i < BOARDSIZE; i++) {
      zobristEnPassant[i] = Math.abs(random.nextLong());
    }
  }

  public Hex88Board(GenericBoard newBoard) {
    // Initialize repetition table
    repetitionTable = new RepetitionTable();

    for (int i = 0; i < stack.length; i++) {
      stack[i] = new Hex88BoardStackEntry();
    }

    // Initialize the position lists
    for (int color : IntColor.values) {
      pawnList[color] = new PositionList();
      knightList[color] = new PositionList();
      bishopList[color] = new PositionList();
      rookList[color] = new PositionList();
      queenList[color] = new PositionList();
      kingList[color] = new PositionList();
    }

    // Initialize the attack list
    for (int i = 0; i < attackHistory.length; i++) {
      for (int j = 0; j < IntColor.ARRAY_DIMENSION; j++) {
        attackHistory[i][j] = new Attack();
      }
    }

    // Initialize the material values and counters
    for (int color : IntColor.values) {
      materialValueAll[color] = 0;
      materialCount[color] = 0;
      materialCountAll[color] = 0;
    }

    // Initialize the board
    for (int position : IntPosition.values) {
      board[position] = IntChessman.NOPIECE;

      GenericPiece genericPiece = newBoard.getPiece(IntPosition.valueOfIntPosition(position));
      if (genericPiece != null) {
        int piece = IntChessman.createPiece(IntChessman.valueOfChessman(genericPiece.chessman), IntColor.valueOfColor(genericPiece.color));
        put(piece, position, true);
      }
    }

    // Initialize en passant
    if (newBoard.getEnPassant() != null) {
      enPassantSquare = IntPosition.valueOfPosition(newBoard.getEnPassant());
      zobristCode ^= zobristEnPassant[IntPosition.valueOfPosition(newBoard.getEnPassant())];
    }

    // Initialize castling
    castling = 0;
    if (newBoard.getCastling(GenericColor.WHITE, GenericCastling.KINGSIDE) != null) {
      castling |= IntCastling.WHITE_KINGSIDE;
      zobristCode ^= zobristCastling[IntCastling.WHITE_KINGSIDE];
    }
    if (newBoard.getCastling(GenericColor.WHITE, GenericCastling.QUEENSIDE) != null) {
      castling |= IntCastling.WHITE_QUEENSIDE;
      zobristCode ^= zobristCastling[IntCastling.WHITE_QUEENSIDE];
    }
    if (newBoard.getCastling(GenericColor.BLACK, GenericCastling.KINGSIDE) != null) {
      castling |= IntCastling.BLACK_KINGSIDE;
      zobristCode ^= zobristCastling[IntCastling.BLACK_KINGSIDE];
    }
    if (newBoard.getCastling(GenericColor.BLACK, GenericCastling.QUEENSIDE) != null) {
      castling |= IntCastling.BLACK_QUEENSIDE;
      zobristCode ^= zobristCastling[IntCastling.BLACK_QUEENSIDE];
    }

    // Initialize the active color
    if (activeColor != IntColor.valueOfColor(newBoard.getActiveColor())) {
      activeColor = IntColor.valueOfColor(newBoard.getActiveColor());
      zobristCode ^= zobristActiveColor;
      pawnZobristCode ^= zobristActiveColor;
    }

    // Initialize the half move clock
    assert newBoard.getHalfMoveClock() >= 0;
    halfMoveClock = newBoard.getHalfMoveClock();

    // Initialize the full move number
    assert newBoard.getFullMoveNumber() > 0;
    setFullMoveNumber(newBoard.getFullMoveNumber());
  }

  public Hex88Board(Hex88Board board) {
    // Initialize the board
    System.arraycopy(board.board, 0, this.board, 0, BOARDSIZE);

    // Initialize the position lists
    for (int color : IntColor.values) {
      pawnList[color] = new PositionList(board.pawnList[color]);
      knightList[color] = new PositionList(board.knightList[color]);
      bishopList[color] = new PositionList(board.bishopList[color]);
      rookList[color] = new PositionList(board.rookList[color]);
      queenList[color] = new PositionList(board.queenList[color]);
      kingList[color] = new PositionList(board.kingList[color]);
    }

    for (int i = 0; i < STACKSIZE; i++) {
      stack[i] = new Hex88BoardStackEntry(board.stack[i]);
    }
    stackSize = board.stackSize;

    zobristCode = board.zobristCode;
    pawnZobristCode = board.pawnZobristCode;

    // Initialize en passant
    enPassantSquare = board.enPassantSquare;

    // Initialize castling
    castling = board.castling;
    System.arraycopy(board.castlingHistory, 0, castlingHistory, 0, STACKSIZE);
    castlingHistorySize = board.castlingHistorySize;

    // Initialize capture
    captureSquare = board.captureSquare;
    System.arraycopy(board.captureHistory, 0, captureHistory, 0, STACKSIZE);
    captureHistorySize = board.captureHistorySize;

    // Initialize the half move clock
    halfMoveClock = board.halfMoveClock;

    // Initialize the full move number
    halfMoveNumber = board.halfMoveNumber;

    // Initialize the active color
    activeColor = board.activeColor;

    // Initialize the material values and counters
    for (int color : IntColor.values) {
      materialValueAll[color] = board.materialValueAll[color];
      materialCount[color] = board.materialCount[color];
      materialCountAll[color] = board.materialCountAll[color];
    }

    // Initialize repetition table
    repetitionTable = new RepetitionTable(board.repetitionTable);

    // Initialize the attack list
    for (int i = 0; i < STACKSIZE + 1; i++) {
      for (int j = 0; j < IntColor.ARRAY_DIMENSION; j++) {
        attackHistory[i][j] = new Attack(board.attackHistory[i][j]);
      }
    }
    attackHistorySize = board.attackHistorySize;
  }

  /**
   * Puts the piece on the board at the given position.
   *
   * @param piece    the piece.
   * @param position the position.
   * @param update   true if we should update, false otherwise.
   */
  private void put(int piece, int position, boolean update) {
    assert piece != IntChessman.NOPIECE;
    assert (position & 0x88) == 0;
    assert board[position] == IntChessman.NOPIECE;

    // Store some variables for later use
    int chessman = IntChessman.getChessman(piece);
    int color = IntChessman.getColor(piece);

    switch (chessman) {
      case IntChessman.PAWN:
        addPosition(position, pawnList[color]);
        materialCountAll[color]++;
        if (update) {
          pawnZobristCode ^= zobristChessman[IntChessman.PAWN][color][position];
        }
        break;
      case IntChessman.KNIGHT:
        addPosition(position, knightList[color]);
        materialCount[color]++;
        materialCountAll[color]++;
        break;
      case IntChessman.BISHOP:
        addPosition(position, bishopList[color]);
        materialCount[color]++;
        materialCountAll[color]++;
        break;
      case IntChessman.ROOK:
        addPosition(position, rookList[color]);
        materialCount[color]++;
        materialCountAll[color]++;
        break;
      case IntChessman.QUEEN:
        addPosition(position, queenList[color]);
        materialCount[color]++;
        materialCountAll[color]++;
        break;
      case IntChessman.KING:
        addPosition(position, kingList[color]);
        break;
      default:
        assert false : chessman;
        break;
    }

    // Update
    board[position] = piece;
    materialValueAll[color] += IntChessman.getValueFromChessman(chessman);
    if (update) {
      zobristCode ^= zobristChessman[chessman][color][position];
    }
  }

  /**
   * Removes the piece from the board at the given position.
   *
   * @param position the position.
   * @param update   true if we should update, false otherwise.
   * @return the removed piece.
   */
  private int remove(int position, boolean update) {
    assert (position & 0x88) == 0;
    assert board[position] != IntChessman.NOPIECE;

    // Get the piece
    int piece = board[position];

    // Store some variables for later use
    int chessman = IntChessman.getChessman(piece);
    int color = IntChessman.getColor(piece);

    switch (chessman) {
      case IntChessman.PAWN:
        removePosition(position, pawnList[color]);
        materialCountAll[color]--;
        if (update) {
          pawnZobristCode ^= zobristChessman[IntChessman.PAWN][color][position];
        }
        break;
      case IntChessman.KNIGHT:
        removePosition(position, knightList[color]);
        materialCount[color]--;
        materialCountAll[color]--;
        break;
      case IntChessman.BISHOP:
        removePosition(position, bishopList[color]);
        materialCount[color]--;
        materialCountAll[color]--;
        break;
      case IntChessman.ROOK:
        removePosition(position, rookList[color]);
        materialCount[color]--;
        materialCountAll[color]--;
        break;
      case IntChessman.QUEEN:
        removePosition(position, queenList[color]);
        materialCount[color]--;
        materialCountAll[color]--;
        break;
      case IntChessman.KING:
        removePosition(position, kingList[color]);
        break;
      default:
        assert false : chessman;
        break;
    }

    // Update
    board[position] = IntChessman.NOPIECE;
    materialValueAll[color] -= IntChessman.getValueFromChessman(chessman);
    if (update) {
      zobristCode ^= zobristChessman[chessman][color][position];
    }

    return piece;
  }

  /**
   * Moves the piece from the start position to the end position.
   *
   * @param start  the start position.
   * @param end    the end position.
   * @param update true if we should update, false otherwise.
   * @return the moved piece.
   */
  private int move(int start, int end, boolean update) {
    assert (start & 0x88) == 0;
    assert (end & 0x88) == 0;
    assert board[start] != IntChessman.NOPIECE;
    assert board[end] == IntChessman.NOPIECE;

    // Get the piece
    int piece = board[start];

    // Store some variables for later use
    int chessman = IntChessman.getChessman(piece);
    int color = IntChessman.getColor(piece);

    switch (chessman) {
      case IntChessman.PAWN:
        removePosition(start, pawnList[color]);
        addPosition(end, pawnList[color]);
        if (update) {
          long[] tempZobristChessman = zobristChessman[IntChessman.PAWN][color];
          pawnZobristCode ^= tempZobristChessman[start];
          pawnZobristCode ^= tempZobristChessman[end];
        }
        break;
      case IntChessman.KNIGHT:
        removePosition(start, knightList[color]);
        addPosition(end, knightList[color]);
        break;
      case IntChessman.BISHOP:
        removePosition(start, bishopList[color]);
        addPosition(end, bishopList[color]);
        break;
      case IntChessman.ROOK:
        removePosition(start, rookList[color]);
        addPosition(end, rookList[color]);
        break;
      case IntChessman.QUEEN:
        removePosition(start, queenList[color]);
        addPosition(end, queenList[color]);
        break;
      case IntChessman.KING:
        removePosition(start, kingList[color]);
        addPosition(end, kingList[color]);
        break;
      default:
        assert false : chessman;
        break;
    }

    // Update
    board[start] = IntChessman.NOPIECE;
    board[end] = piece;
    if (update) {
      long[] tempZobristChessman = zobristChessman[chessman][color];
      zobristCode ^= tempZobristChessman[start];
      zobristCode ^= tempZobristChessman[end];
    }

    return piece;
  }

  public GenericBoard getBoard() {
    GenericBoard newBoard = new GenericBoard();

    // Set chessmen
    for (GenericColor color : GenericColor.values()) {
      int intColor = IntColor.valueOfColor(color);

      for (int index = 0; index < pawnList[intColor].size; index++) {
        int intPosition = pawnList[intColor].position[index];
        assert intPosition != IntPosition.NOPOSITION;
        assert IntChessman.getChessman(board[intPosition]) == IntChessman.PAWN;
        assert IntChessman.getColor(board[intPosition]) == intColor;

        GenericPosition position = IntPosition.valueOfIntPosition(intPosition);
        newBoard.setPiece(GenericPiece.valueOf(color, GenericChessman.PAWN), position);
      }

      for (int index = 0; index < knightList[intColor].size; index++) {
        int intPosition = knightList[intColor].position[index];
        assert intPosition != IntPosition.NOPOSITION;
        assert IntChessman.getChessman(board[intPosition]) == IntChessman.KNIGHT;
        assert IntChessman.getColor(board[intPosition]) == intColor;

        GenericPosition position = IntPosition.valueOfIntPosition(intPosition);
        newBoard.setPiece(GenericPiece.valueOf(color, GenericChessman.KNIGHT), position);
      }

      for (int index = 0; index < bishopList[intColor].size; index++) {
        int intPosition = bishopList[intColor].position[index];
        assert intPosition != IntPosition.NOPOSITION;
        assert IntChessman.getChessman(board[intPosition]) == IntChessman.BISHOP;
        assert IntChessman.getColor(board[intPosition]) == intColor;

        GenericPosition position = IntPosition.valueOfIntPosition(intPosition);
        newBoard.setPiece(GenericPiece.valueOf(color, GenericChessman.BISHOP), position);
      }

      for (int index = 0; index < rookList[intColor].size; index++) {
        int intPosition = rookList[intColor].position[index];
        assert intPosition != IntPosition.NOPOSITION;
        assert IntChessman.getChessman(board[intPosition]) == IntChessman.ROOK;
        assert IntChessman.getColor(board[intPosition]) == intColor;

        GenericPosition position = IntPosition.valueOfIntPosition(intPosition);
        newBoard.setPiece(GenericPiece.valueOf(color, GenericChessman.ROOK), position);
      }

      for (int index = 0; index < queenList[intColor].size; index++) {
        int intPosition = queenList[intColor].position[index];
        assert intPosition != IntPosition.NOPOSITION;
        assert IntChessman.getChessman(board[intPosition]) == IntChessman.QUEEN;
        assert IntChessman.getColor(board[intPosition]) == intColor;

        GenericPosition position = IntPosition.valueOfIntPosition(intPosition);
        newBoard.setPiece(GenericPiece.valueOf(color, GenericChessman.QUEEN), position);
      }

      assert kingList[intColor].size == 1;
      int intPosition = kingList[intColor].position[0];
      assert intPosition != IntPosition.NOPOSITION;
      assert IntChessman.getChessman(board[intPosition]) == IntChessman.KING;
      assert IntChessman.getColor(board[intPosition]) == intColor;

      GenericPosition position = IntPosition.valueOfIntPosition(intPosition);
      newBoard.setPiece(GenericPiece.valueOf(color, GenericChessman.KING), position);
    }

    // Set active color
    newBoard.setActiveColor(IntColor.valueOfIntColor(activeColor));

    // Set castling
    if ((castling & IntCastling.WHITE_KINGSIDE) != 0) {
      newBoard.setCastling(GenericColor.WHITE, GenericCastling.KINGSIDE, GenericFile.Fh);
    }
    if ((castling & IntCastling.WHITE_QUEENSIDE) != 0) {
      newBoard.setCastling(GenericColor.WHITE, GenericCastling.QUEENSIDE, GenericFile.Fa);
    }
    if ((castling & IntCastling.BLACK_KINGSIDE) != 0) {
      newBoard.setCastling(GenericColor.BLACK, GenericCastling.KINGSIDE, GenericFile.Fh);
    }
    if ((castling & IntCastling.BLACK_QUEENSIDE) != 0) {
      newBoard.setCastling(GenericColor.BLACK, GenericCastling.QUEENSIDE, GenericFile.Fa);
    }

    // Set en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      newBoard.setEnPassant(IntPosition.valueOfIntPosition(enPassantSquare));
    }

    // Set half move clock
    newBoard.setHalfMoveClock(halfMoveClock);

    // Set full move number
    newBoard.setFullMoveNumber(getFullMoveNumber());

    return newBoard;
  }

  public int getFullMoveNumber() {
    return halfMoveNumber / 2;
  }

  private void setFullMoveNumber(int fullMoveNumber) {
    assert fullMoveNumber > 0;

    halfMoveNumber = fullMoveNumber * 2;
    if (activeColor == IntColor.valueOfColor(GenericColor.BLACK)) {
      halfMoveNumber++;
    }
  }

  public int getGamePhase() {
    if (materialValueAll[IntColor.WHITE] >= GAMEPHASE_OPENING_VALUE && materialValueAll[IntColor.BLACK] >= GAMEPHASE_OPENING_VALUE) {
      return IntGamePhase.OPENING;
    } else if (materialValueAll[IntColor.WHITE] <= GAMEPHASE_ENDGAME_VALUE || materialValueAll[IntColor.BLACK] <= GAMEPHASE_ENDGAME_VALUE
      || materialCount[IntColor.WHITE] <= GAMEPHASE_ENDGAME_COUNT || materialCount[IntColor.BLACK] <= GAMEPHASE_ENDGAME_COUNT) {
      return IntGamePhase.ENDGAME;
    } else {
      return IntGamePhase.MIDDLE;
    }
  }

  /**
   * Returns the evaluation value mix from the opening and endgame values depending on the current game phase.
   * This allows us to make a smooth transition from the opening to the endgame.
   *
   * @param myColor the color.
   * @param opening the opening evaluation value.
   * @param endgame the endgame evaluation value.
   * @return the evaluation value mix from the opening and endgame values depending on the current game phase.
   */
  public int getGamePhaseEvaluation(int myColor, int opening, int endgame) {
    int intervalMaterial = materialValueAll[myColor];

    if (intervalMaterial >= GAMEPHASE_OPENING_VALUE) {
      intervalMaterial = GAMEPHASE_INTERVAL;
    } else if (intervalMaterial <= GAMEPHASE_ENDGAME_VALUE) {
      intervalMaterial = 0;
    } else {
      intervalMaterial -= GAMEPHASE_ENDGAME_VALUE;
    }

    return (((opening - endgame) * intervalMaterial) / GAMEPHASE_INTERVAL) + endgame;
  }

  /**
   * Returns whether this board state is a repetition.
   *
   * @return true if this board state is a repetition, false otherwise.
   */
  public boolean isRepetition() {
    return repetitionTable.exists(zobristCode);
  }

  /**
   * Returns whether this move checks the opponent king.
   *
   * @param move the move.
   * @return true if this move checks the opponent king.
   */
  public boolean isCheckingMove(int move) {
    assert move != IntMove.NOMOVE;

    int chessmanColor = IntMove.getChessmanColor(move);
    int endPosition = IntMove.getEnd(move);
    int enemyKingColor = IntColor.switchColor(chessmanColor);
    int enemyKingPosition = kingList[enemyKingColor].position[0];

    switch (IntMove.getType(move)) {
      case IntMove.NORMAL:
      case IntMove.PAWNDOUBLE:
        int chessman = IntMove.getChessman(move);

        // Direct attacks
        if (canAttack(chessman, chessmanColor, endPosition, enemyKingPosition)) {
          return true;
        }

        int startPosition = IntMove.getStart(move);

        if (isPinned(startPosition, enemyKingColor)) {
          // We are pinned. Test if we move on the line.
          int attackDeltaStart = AttackVector.delta[enemyKingPosition - startPosition + 127];
          int attackDeltaEnd = AttackVector.delta[enemyKingPosition - endPosition + 127];
          return attackDeltaStart != attackDeltaEnd;
        }
        // Indirect attacks
        break;
      case IntMove.PAWNPROMOTION:
      case IntMove.ENPASSANT:
        // We do a slow test for complex moves
        makeMove(move);
        boolean isCheck = isAttacked(enemyKingPosition, chessmanColor);
        undoMove(move);
        return isCheck;
      case IntMove.CASTLING:
        int rookEnd = IntPosition.NOPOSITION;

        if (endPosition == IntPosition.g1) {
          assert chessmanColor == IntColor.WHITE;
          rookEnd = IntPosition.f1;
        } else if (endPosition == IntPosition.g8) {
          assert chessmanColor == IntColor.BLACK;
          rookEnd = IntPosition.f8;
        } else if (endPosition == IntPosition.c1) {
          assert chessmanColor == IntColor.WHITE;
          rookEnd = IntPosition.d1;
        } else if (endPosition == IntPosition.c8) {
          assert chessmanColor == IntColor.BLACK;
          rookEnd = IntPosition.d8;
        } else {
          assert false : endPosition;
        }

        return canAttack(IntChessman.ROOK, chessmanColor, rookEnd, enemyKingPosition);
      case IntMove.NULL:
        assert false;
        break;
      default:
        assert false : IntMove.getType(move);
        break;
    }

    return false;
  }

  public boolean isPinned(int chessmanPosition, int kingColor) {
    assert chessmanPosition != IntPosition.NOPOSITION;
    assert kingColor != IntColor.NOCOLOR;

    int myKingPosition = kingList[kingColor].position[0];

    // We can only be pinned on an attack line
    int vector = AttackVector.vector[myKingPosition - chessmanPosition + 127];
    if (vector == AttackVector.N || vector == AttackVector.K) {
      // No line
      return false;
    }

    int delta = AttackVector.delta[myKingPosition - chessmanPosition + 127];

    // Walk towards the king
    int end = chessmanPosition + delta;
    assert (end & 0x88) == 0;
    while (board[end] == IntChessman.NOPIECE) {
      end += delta;
      assert (end & 0x88) == 0;
    }
    if (end != myKingPosition) {
      // There's a blocker between me and the king
      return false;
    }

    // Walk away from the king
    end = chessmanPosition - delta;
    while ((end & 0x88) == 0) {
      int attacker = board[end];
      if (attacker != IntChessman.NOPIECE) {
        int attackerColor = IntChessman.getColor(attacker);

        return kingColor != attackerColor && canSliderPseudoAttack(attacker, end, myKingPosition);
      } else {
        end -= delta;
      }
    }

    return false;
  }

  /**
   * Returns whether or not the attacker can attack the target position. The
   * method does not check if a slider can reach the position.
   *
   * @param attacker       the attacker.
   * @param targetPosition the target position.
   * @return if the attacker can attack the target position.
   */
  public boolean canSliderPseudoAttack(int attacker, int attackerPosition, int targetPosition) {
    assert attacker != IntChessman.NOPIECE;
    assert (attackerPosition & 0x88) == 0;
    assert (targetPosition & 0x88) == 0;

    int attackVector = AttackVector.N;

    switch (IntChessman.getChessman(attacker)) {
      case IntChessman.PAWN:
        break;
      case IntChessman.KNIGHT:
        break;
      case IntChessman.BISHOP:
        attackVector = AttackVector.vector[targetPosition - attackerPosition + 127];
        switch (attackVector) {
          case AttackVector.u:
          case AttackVector.d:
          case AttackVector.D:
            return true;
          default:
            break;
        }
        break;
      case IntChessman.ROOK:
        attackVector = AttackVector.vector[targetPosition - attackerPosition + 127];
        switch (attackVector) {
          case AttackVector.s:
          case AttackVector.S:
            return true;
          default:
            break;
        }
        break;
      case IntChessman.QUEEN:
        attackVector = AttackVector.vector[targetPosition - attackerPosition + 127];
        switch (attackVector) {
          case AttackVector.u:
          case AttackVector.d:
          case AttackVector.s:
          case AttackVector.D:
          case AttackVector.S:
            return true;
          default:
            break;
        }
        break;
      case IntChessman.KING:
        break;
      default:
        assert false : IntChessman.getChessman(attacker);
        break;
    }

    return false;
  }

  /**
   * Returns the attacks on the king.
   *
   * @param color the Color of the target king.
   */
  public Attack getAttack(int color) {
    Attack attack = attackHistory[attackHistorySize][color];
    if (attack.count != Attack.NOATTACK) {
      return attack;
    }

    assert kingList[color].size == 1;

    int attackerColor = IntColor.switchColor(color);
    getAttack(attack, kingList[color].position[0], attackerColor, false);

    return attack;
  }

  /**
   * Returns whether or not the position is attacked.
   *
   * @param targetPosition the IntPosition.
   * @param attackerColor  the attacker color.
   * @return true if the position is attacked, false otherwise.
   */
  public boolean isAttacked(int targetPosition, int attackerColor) {
    assert (targetPosition & 0x88) == 0;
    assert attackerColor != IntColor.NOCOLOR;

    return getAttack(tempAttack, targetPosition, attackerColor, true);
  }

  /**
   * Returns all attacks to the target position.
   *
   * @param attack         the attack to fill the information.
   * @param targetPosition the target position.
   * @param attackerColor  the attacker color.
   * @param stop           whether we should only check.
   * @return true if the position can be attacked, false otherwise.
   */
  private boolean getAttack(Attack attack, int targetPosition, int attackerColor, boolean stop) {
    assert attack != null;
    assert targetPosition != IntPosition.NOPOSITION;
    assert attackerColor != IntColor.NOCOLOR;

    attack.count = 0;

    // Pawn attacks
    int pawnPiece = IntChessman.WHITE_PAWN;
    int sign = -1;
    if (attackerColor == IntColor.BLACK) {
      pawnPiece = IntChessman.BLACK_PAWN;
      sign = 1;
    } else {
      assert attackerColor == IntColor.WHITE;
    }
    int pawnAttackerPosition = targetPosition + sign * 15;
    if ((pawnAttackerPosition & 0x88) == 0) {
      int pawn = board[pawnAttackerPosition];
      if (pawn != IntChessman.NOPIECE && pawn == pawnPiece) {
        if (stop) {
          return true;
        }
        assert AttackVector.delta[targetPosition - pawnAttackerPosition + 127] == sign * -15;
        attack.position[attack.count] = pawnAttackerPosition;
        attack.delta[attack.count] = sign * -15;
        attack.count++;
      }
    }
    pawnAttackerPosition = targetPosition + sign * 17;
    if ((pawnAttackerPosition & 0x88) == 0) {
      int pawn = board[pawnAttackerPosition];
      if (pawn != IntChessman.NOPIECE && pawn == pawnPiece) {
        if (stop) {
          return true;
        }
        assert AttackVector.delta[targetPosition - pawnAttackerPosition + 127] == sign * -17;
        attack.position[attack.count] = pawnAttackerPosition;
        attack.delta[attack.count] = sign * -17;
        attack.count++;
      }
    }
    PositionList tempChessmanList = knightList[attackerColor];
    for (int index = 0; index < tempChessmanList.size; index++) {
      int attackerPosition = tempChessmanList.position[index];
      assert IntChessman.getChessman(board[attackerPosition]) == IntChessman.KNIGHT;
      assert attackerPosition != IntPosition.NOPOSITION;
      assert board[attackerPosition] != IntChessman.NOPIECE;
      assert attackerColor == IntChessman.getColor(board[attackerPosition]);
      if (canAttack(IntChessman.KNIGHT, attackerColor, attackerPosition, targetPosition)) {
        if (stop) {
          return true;
        }
        int attackDelta = AttackVector.delta[targetPosition - attackerPosition + 127];
        assert attackDelta != 0;
        attack.position[attack.count] = attackerPosition;
        attack.delta[attack.count] = attackDelta;
        attack.count++;
      }
    }
    tempChessmanList = bishopList[attackerColor];
    for (int index = 0; index < tempChessmanList.size; index++) {
      int attackerPosition = tempChessmanList.position[index];
      assert IntChessman.getChessman(board[attackerPosition]) == IntChessman.BISHOP;
      assert attackerPosition != IntPosition.NOPOSITION;
      assert board[attackerPosition] != IntChessman.NOPIECE;
      assert attackerColor == IntChessman.getColor(board[attackerPosition]);
      if (canAttack(IntChessman.BISHOP, attackerColor, attackerPosition, targetPosition)) {
        if (stop) {
          return true;
        }
        int attackDelta = AttackVector.delta[targetPosition - attackerPosition + 127];
        assert attackDelta != 0;
        attack.position[attack.count] = attackerPosition;
        attack.delta[attack.count] = attackDelta;
        attack.count++;
      }
    }
    tempChessmanList = rookList[attackerColor];
    for (int index = 0; index < tempChessmanList.size; index++) {
      int attackerPosition = tempChessmanList.position[index];
      assert IntChessman.getChessman(board[attackerPosition]) == IntChessman.ROOK;
      assert attackerPosition != IntPosition.NOPOSITION;
      assert board[attackerPosition] != IntChessman.NOPIECE;
      assert attackerColor == IntChessman.getColor(board[attackerPosition]);
      if (canAttack(IntChessman.ROOK, attackerColor, attackerPosition, targetPosition)) {
        if (stop) {
          return true;
        }
        int attackDelta = AttackVector.delta[targetPosition - attackerPosition + 127];
        assert attackDelta != 0;
        attack.position[attack.count] = attackerPosition;
        attack.delta[attack.count] = attackDelta;
        attack.count++;
      }
    }
    tempChessmanList = queenList[attackerColor];
    for (int index = 0; index < tempChessmanList.size; index++) {
      int attackerPosition = tempChessmanList.position[index];
      assert IntChessman.getChessman(board[attackerPosition]) == IntChessman.QUEEN;
      assert attackerPosition != IntPosition.NOPOSITION;
      assert board[attackerPosition] != IntChessman.NOPIECE;
      assert attackerColor == IntChessman.getColor(board[attackerPosition]);
      if (canAttack(IntChessman.QUEEN, attackerColor, attackerPosition, targetPosition)) {
        if (stop) {
          return true;
        }
        int attackDelta = AttackVector.delta[targetPosition - attackerPosition + 127];
        assert attackDelta != 0;
        attack.position[attack.count] = attackerPosition;
        attack.delta[attack.count] = attackDelta;
        attack.count++;
      }
    }
    assert kingList[attackerColor].size == 1;
    int attackerPosition = kingList[attackerColor].position[0];
    assert IntChessman.getChessman(board[attackerPosition]) == IntChessman.KING;
    assert attackerPosition != IntPosition.NOPOSITION;
    assert board[attackerPosition] != IntChessman.NOPIECE;
    assert attackerColor == IntChessman.getColor(board[attackerPosition]);
    if (canAttack(IntChessman.KING, attackerColor, attackerPosition, targetPosition)) {
      if (stop) {
        return true;
      }
      int attackDelta = AttackVector.delta[targetPosition - attackerPosition + 127];
      assert attackDelta != 0;
      attack.position[attack.count] = attackerPosition;
      attack.delta[attack.count] = attackDelta;
      attack.count++;
    }

    return false;
  }

  /**
   * Returns whether or not the attacker can attack the target position.
   *
   * @param attackerChessman the attacker chessman.
   * @param attackerColor    the attacker color.
   * @param attackerPosition the attacker position.
   * @param targetPosition   the target position.
   * @return if the attacker can attack the target position.
   */
  public boolean canAttack(int attackerChessman, int attackerColor, int attackerPosition, int targetPosition) {
    assert attackerChessman != IntChessman.NOPIECE;
    assert attackerColor != IntColor.NOCOLOR;
    assert (attackerPosition & 0x88) == 0;
    assert (targetPosition & 0x88) == 0;

    int attackVector = AttackVector.vector[targetPosition - attackerPosition + 127];

    switch (attackerChessman) {
      case IntChessman.PAWN:
        if (attackVector == AttackVector.u && attackerColor == IntColor.WHITE) {
          return true;
        } else if (attackVector == AttackVector.d && attackerColor == IntColor.BLACK) {
          return true;
        }
        break;
      case IntChessman.KNIGHT:
        if (attackVector == AttackVector.K) {
          return true;
        }
        break;
      case IntChessman.BISHOP:
        switch (attackVector) {
          case AttackVector.u:
          case AttackVector.d:
            return true;
          case AttackVector.D:
            if (canSliderAttack(attackerPosition, targetPosition)) {
              return true;
            }
            break;
          default:
            break;
        }
        break;
      case IntChessman.ROOK:
        switch (attackVector) {
          case AttackVector.s:
            return true;
          case AttackVector.S:
            if (canSliderAttack(attackerPosition, targetPosition)) {
              return true;
            }
            break;
          default:
            break;
        }
        break;
      case IntChessman.QUEEN:
        switch (attackVector) {
          case AttackVector.u:
          case AttackVector.d:
          case AttackVector.s:
            return true;
          case AttackVector.D:
          case AttackVector.S:
            if (canSliderAttack(attackerPosition, targetPosition)) {
              return true;
            }
            break;
          default:
            break;
        }
        break;
      case IntChessman.KING:
        switch (attackVector) {
          case AttackVector.u:
          case AttackVector.d:
          case AttackVector.s:
            return true;
          default:
            break;
        }
        break;
      default:
        assert false : attackerChessman;
        break;
    }

    return false;
  }

  /**
   * Returns whether or not the slider can attack the target position.
   *
   * @param attackerPosition the attacker position.
   * @param targetPosition   the target position.
   * @return true if the slider can attack the target position.
   */
  private boolean canSliderAttack(int attackerPosition, int targetPosition) {
    assert (attackerPosition & 0x88) == 0;
    assert (targetPosition & 0x88) == 0;

    int attackDelta = AttackVector.delta[targetPosition - attackerPosition + 127];

    int end = attackerPosition + attackDelta;
    while ((end & 0x88) == 0 && end != targetPosition && board[end] == IntChessman.NOPIECE) {
      end += attackDelta;
    }

    return end == targetPosition;
  }

  public void makeMove(int move) {
    // Get current stack entry
    Hex88BoardStackEntry currentStackEntry = stack[stackSize];

    // Save history
    currentStackEntry.zobristHistory = zobristCode;
    currentStackEntry.pawnZobristHistory = pawnZobristCode;
    currentStackEntry.halfMoveClockHistory = halfMoveClock;
    currentStackEntry.enPassantHistory = enPassantSquare;
    currentStackEntry.captureSquareHistory = captureSquare;

    // Update stack size
    stackSize++;
    assert stackSize < STACKSIZE;

    int type = IntMove.getType(move);

    switch (type) {
      case IntMove.NORMAL:
        repetitionTable.put(zobristCode);
        makeMoveNormal(move);
        break;
      case IntMove.PAWNDOUBLE:
        repetitionTable.put(zobristCode);
        makeMovePawnDouble(move);
        break;
      case IntMove.PAWNPROMOTION:
        repetitionTable.put(zobristCode);
        makeMovePawnPromotion(move);
        break;
      case IntMove.ENPASSANT:
        repetitionTable.put(zobristCode);
        makeMoveEnPassant(move);
        break;
      case IntMove.CASTLING:
        repetitionTable.put(zobristCode);
        makeMoveCastling(move);
        break;
      case IntMove.NULL:
        makeMoveNull();
        break;
      default:
        throw new IllegalArgumentException();
    }

    // Update half move number
    halfMoveNumber++;

    // Update active color
    activeColor = IntColor.switchColor(activeColor);
    zobristCode ^= zobristActiveColor;
    pawnZobristCode ^= zobristActiveColor;

    // Update attack list
    attackHistorySize++;
    attackHistory[attackHistorySize][IntColor.WHITE].count = Attack.NOATTACK;
    attackHistory[attackHistorySize][IntColor.BLACK].count = Attack.NOATTACK;
  }

  public void undoMove(int move) {
    int type = IntMove.getType(move);

    // Update attack list
    attackHistorySize--;

    // Update active color
    activeColor = IntColor.switchColor(activeColor);

    // Update half move number
    halfMoveNumber--;

    // Update stack size
    stackSize--;
    assert stackSize >= 0;

    // Get current stack entry
    Hex88BoardStackEntry currentStackEntry = stack[stackSize];

    // Restore zobrist history
    zobristCode = currentStackEntry.zobristHistory;
    pawnZobristCode = currentStackEntry.pawnZobristHistory;
    halfMoveClock = currentStackEntry.halfMoveClockHistory;
    enPassantSquare = currentStackEntry.enPassantHistory;
    captureSquare = currentStackEntry.captureSquareHistory;

    switch (type) {
      case IntMove.NORMAL:
        undoMoveNormal(move);
        repetitionTable.remove(zobristCode);
        break;
      case IntMove.PAWNDOUBLE:
        undoMovePawnDouble(move);
        repetitionTable.remove(zobristCode);
        break;
      case IntMove.PAWNPROMOTION:
        undoMovePawnPromotion(move);
        repetitionTable.remove(zobristCode);
        break;
      case IntMove.ENPASSANT:
        undoMoveEnPassant(move);
        repetitionTable.remove(zobristCode);
        break;
      case IntMove.CASTLING:
        undoMoveCastling(move);
        repetitionTable.remove(zobristCode);
        break;
      case IntMove.NULL:
        break;
      default:
        throw new IllegalArgumentException();
    }
  }

  private void makeMoveNormal(int move) {
    // Save the castling rights
    castlingHistory[castlingHistorySize++] = castling;
    int newCastling = castling;

    // Save the captured chessman
    int endPosition = IntMove.getEnd(move);
    int target = IntChessman.NOPIECE;
    if (board[endPosition] != IntChessman.NOPIECE) {
      target = remove(endPosition, true);
      assert IntMove.getTarget(move) != IntChessman.NOPIECE : IntMove.toString(move);
      captureHistory[captureHistorySize++] = target;
      captureSquare = endPosition;

      switch (endPosition) {
        case IntPosition.a1:
          newCastling &= ~IntCastling.WHITE_QUEENSIDE;
          break;
        case IntPosition.a8:
          newCastling &= ~IntCastling.BLACK_QUEENSIDE;
          break;
        case IntPosition.h1:
          newCastling &= ~IntCastling.WHITE_KINGSIDE;
          break;
        case IntPosition.h8:
          newCastling &= ~IntCastling.BLACK_KINGSIDE;
          break;
        case IntPosition.e1:
          newCastling &= ~(IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE);
          break;
        case IntPosition.e8:
          newCastling &= ~(IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
          break;
        default:
          break;
      }
      if (newCastling != castling) {
        assert (newCastling ^ castling) == IntCastling.WHITE_KINGSIDE
          || (newCastling ^ castling) == IntCastling.WHITE_QUEENSIDE
          || (newCastling ^ castling) == IntCastling.BLACK_KINGSIDE
          || (newCastling ^ castling) == IntCastling.BLACK_QUEENSIDE
          || (newCastling ^ castling) == (IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE)
          || (newCastling ^ castling) == (IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
        zobristCode ^= zobristCastling[newCastling ^ castling];
        castling = newCastling;
      }
    } else {
      captureSquare = IntPosition.NOPOSITION;
    }

    // Move the piece
    int startPosition = IntMove.getStart(move);
    int piece = move(startPosition, endPosition, true);
    int chessman = IntChessman.getChessman(piece);

    // Update castling
    switch (startPosition) {
      case IntPosition.a1:
        newCastling &= ~IntCastling.WHITE_QUEENSIDE;
        break;
      case IntPosition.a8:
        newCastling &= ~IntCastling.BLACK_QUEENSIDE;
        break;
      case IntPosition.h1:
        newCastling &= ~IntCastling.WHITE_KINGSIDE;
        break;
      case IntPosition.h8:
        newCastling &= ~IntCastling.BLACK_KINGSIDE;
        break;
      case IntPosition.e1:
        newCastling &= ~(IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE);
        break;
      case IntPosition.e8:
        newCastling &= ~(IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
        break;
      default:
        break;
    }
    if (newCastling != castling) {
      assert (newCastling ^ castling) == IntCastling.WHITE_KINGSIDE
        || (newCastling ^ castling) == IntCastling.WHITE_QUEENSIDE
        || (newCastling ^ castling) == IntCastling.BLACK_KINGSIDE
        || (newCastling ^ castling) == IntCastling.BLACK_QUEENSIDE
        || (newCastling ^ castling) == (IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE)
        || (newCastling ^ castling) == (IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
      zobristCode ^= zobristCastling[newCastling ^ castling];
      castling = newCastling;
    }

    // Update en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      zobristCode ^= zobristEnPassant[enPassantSquare];
      enPassantSquare = IntPosition.NOPOSITION;
    }

    // Update half move clock
    if (chessman == IntChessman.PAWN || target != IntChessman.NOPIECE) {
      halfMoveClock = 0;
    } else {
      halfMoveClock++;
    }
  }

  private void undoMoveNormal(int move) {
    // Move the chessman
    int startPosition = IntMove.getStart(move);
    int endPosition = IntMove.getEnd(move);
    move(endPosition, startPosition, false);

    // Restore the captured chessman
    int target = IntMove.getTarget(move);
    if (target != IntChessman.NOPIECE) {
      put(captureHistory[--captureHistorySize], endPosition, false);
    }

    // Restore the castling rights
    castling = castlingHistory[--castlingHistorySize];
  }

  private void makeMovePawnPromotion(int move) {
    // Remove the pawn at the start position
    int startPosition = IntMove.getStart(move);
    int pawn = remove(startPosition, true);
    assert IntChessman.getChessman(pawn) == IntChessman.PAWN;
    int pawnColor = IntChessman.getColor(pawn);
    assert IntChessman.getChessman(pawn) == IntMove.getChessman(move);
    assert pawnColor == IntMove.getChessmanColor(move);

    // Save the captured chessman
    int endPosition = IntMove.getEnd(move);
    int target = IntChessman.NOPIECE;
    if (board[endPosition] != IntChessman.NOPIECE) {
      // Save the castling rights
      castlingHistory[castlingHistorySize++] = castling;
      int newCastling = castling;

      target = remove(endPosition, true);
      assert IntMove.getTarget(move) != IntChessman.NOPIECE;
      captureHistory[captureHistorySize++] = target;
      captureSquare = endPosition;

      switch (endPosition) {
        case IntPosition.a1:
          newCastling &= ~IntCastling.WHITE_QUEENSIDE;
          break;
        case IntPosition.a8:
          newCastling &= ~IntCastling.BLACK_QUEENSIDE;
          break;
        case IntPosition.h1:
          newCastling &= ~IntCastling.WHITE_KINGSIDE;
          break;
        case IntPosition.h8:
          newCastling &= ~IntCastling.BLACK_KINGSIDE;
          break;
        case IntPosition.e1:
          newCastling &= ~(IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE);
          break;
        case IntPosition.e8:
          newCastling &= ~(IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
          break;
        default:
          break;
      }
      if (newCastling != castling) {
        assert (newCastling ^ castling) == IntCastling.WHITE_KINGSIDE
          || (newCastling ^ castling) == IntCastling.WHITE_QUEENSIDE
          || (newCastling ^ castling) == IntCastling.BLACK_KINGSIDE
          || (newCastling ^ castling) == IntCastling.BLACK_QUEENSIDE
          || (newCastling ^ castling) == (IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE)
          || (newCastling ^ castling) == (IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
        zobristCode ^= zobristCastling[newCastling ^ castling];
        castling = newCastling;
      }
    } else {
      captureSquare = IntPosition.NOPOSITION;
    }

    // Create the promotion chessman
    int promotion = IntMove.getPromotion(move);
    int promotionPiece = IntChessman.createPromotion(promotion, pawnColor);
    put(promotionPiece, endPosition, true);

    // Update en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      zobristCode ^= zobristEnPassant[enPassantSquare];
      enPassantSquare = IntPosition.NOPOSITION;
    }

    // Update half move clock
    halfMoveClock = 0;
  }

  private void undoMovePawnPromotion(int move) {
    // Remove the promotion chessman at the end position
    int endPosition = IntMove.getEnd(move);
    remove(endPosition, false);

    // Restore the captured chessman
    int target = IntMove.getTarget(move);
    if (target != IntChessman.NOPIECE) {
      put(captureHistory[--captureHistorySize], endPosition, false);

      // Restore the castling rights
      castling = castlingHistory[--castlingHistorySize];
    }

    // Put the pawn at the start position
    int pawnChessman = IntMove.getChessman(move);
    int pawnColor = IntMove.getChessmanColor(move);
    int pawnPiece = IntChessman.createPiece(pawnChessman, pawnColor);
    put(pawnPiece, IntMove.getStart(move), false);
  }

  private void makeMovePawnDouble(int move) {
    // Move the pawn
    int startPosition = IntMove.getStart(move);
    int endPosition = IntMove.getEnd(move);
    int pawn = move(startPosition, endPosition, true);
    int pawnColor = IntChessman.getColor(pawn);

    assert IntChessman.getChessman(pawn) == IntChessman.PAWN;
    assert (startPosition >>> 4 == 1 && pawnColor == IntColor.WHITE) || (startPosition >>> 4 == 6 && pawnColor == IntColor.BLACK) : getBoard().toString() + ":" + IntMove.toString(move);
    assert (endPosition >>> 4 == 3 && pawnColor == IntColor.WHITE) || (endPosition >>> 4 == 4 && pawnColor == IntColor.BLACK);
    assert Math.abs(startPosition - endPosition) == 32;

    // Update the capture square
    captureSquare = IntPosition.NOPOSITION;

    // Calculate the en passant position
    int targetPosition;
    if (pawnColor == IntColor.WHITE) {
      targetPosition = endPosition - 16;
    } else {
      assert pawnColor == IntColor.BLACK;

      targetPosition = endPosition + 16;
    }

    assert (targetPosition & 0x88) == 0;
    assert Math.abs(startPosition - targetPosition) == 16;

    // Update en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      zobristCode ^= zobristEnPassant[enPassantSquare];
    }

    enPassantSquare = targetPosition;
    zobristCode ^= zobristEnPassant[targetPosition];

    // Update half move clock
    halfMoveClock = 0;
  }

  private void undoMovePawnDouble(int move) {
    // Move the pawn
    move(IntMove.getEnd(move), IntMove.getStart(move), false);
  }

  private void makeMoveCastling(int move) {
    // Save the castling rights
    castlingHistory[castlingHistorySize++] = castling;
    int newCastling = castling;

    // Move the king
    int kingStartPosition = IntMove.getStart(move);
    int kingEndPosition = IntMove.getEnd(move);
    int king = move(kingStartPosition, kingEndPosition, true);
    assert IntChessman.getChessman(king) == IntChessman.KING;

    // Get the rook positions
    int rookStartPosition;
    int rookEndPosition;
    switch (kingEndPosition) {
      case IntPosition.g1:
        rookStartPosition = IntPosition.h1;
        rookEndPosition = IntPosition.f1;
        newCastling &= ~(IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE);
        break;
      case IntPosition.c1:
        rookStartPosition = IntPosition.a1;
        rookEndPosition = IntPosition.d1;
        newCastling &= ~(IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE);
        break;
      case IntPosition.g8:
        rookStartPosition = IntPosition.h8;
        rookEndPosition = IntPosition.f8;
        newCastling &= ~(IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
        break;
      case IntPosition.c8:
        rookStartPosition = IntPosition.a8;
        rookEndPosition = IntPosition.d8;
        newCastling &= ~(IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
        break;
      default:
        throw new IllegalArgumentException();
    }

    // Move the rook
    int rook = move(rookStartPosition, rookEndPosition, true);
    assert IntChessman.getChessman(rook) == IntChessman.ROOK;

    // Update castling
    assert (newCastling ^ castling) == IntCastling.WHITE_KINGSIDE
      || (newCastling ^ castling) == IntCastling.WHITE_QUEENSIDE
      || (newCastling ^ castling) == IntCastling.BLACK_KINGSIDE
      || (newCastling ^ castling) == IntCastling.BLACK_QUEENSIDE
      || (newCastling ^ castling) == (IntCastling.WHITE_KINGSIDE | IntCastling.WHITE_QUEENSIDE)
      || (newCastling ^ castling) == (IntCastling.BLACK_KINGSIDE | IntCastling.BLACK_QUEENSIDE);
    zobristCode ^= zobristCastling[newCastling ^ castling];
    castling = newCastling;

    // Update the capture square
    captureSquare = IntPosition.NOPOSITION;

    // Update en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      zobristCode ^= zobristEnPassant[enPassantSquare];
      enPassantSquare = IntPosition.NOPOSITION;
    }

    // Update half move clock
    halfMoveClock++;
  }

  private void undoMoveCastling(int move) {
    int kingEndPosition = IntMove.getEnd(move);

    // Get the rook positions
    int rookStartPosition;
    int rookEndPosition;
    switch (kingEndPosition) {
      case IntPosition.g1:
        rookStartPosition = IntPosition.h1;
        rookEndPosition = IntPosition.f1;
        break;
      case IntPosition.c1:
        rookStartPosition = IntPosition.a1;
        rookEndPosition = IntPosition.d1;
        break;
      case IntPosition.g8:
        rookStartPosition = IntPosition.h8;
        rookEndPosition = IntPosition.f8;
        break;
      case IntPosition.c8:
        rookStartPosition = IntPosition.a8;
        rookEndPosition = IntPosition.d8;
        break;
      default:
        throw new IllegalArgumentException();
    }

    // Move the rook
    move(rookEndPosition, rookStartPosition, false);

    // Move the king
    move(kingEndPosition, IntMove.getStart(move), false);

    // Restore the castling rights
    castling = castlingHistory[--castlingHistorySize];
  }

  private void makeMoveEnPassant(int move) {
    // Move the pawn
    int startPosition = IntMove.getStart(move);
    int endPosition = IntMove.getEnd(move);
    int pawn = move(startPosition, endPosition, true);
    assert IntChessman.getChessman(pawn) == IntChessman.PAWN;
    int pawnColor = IntChessman.getColor(pawn);

    // Calculate the en passant position
    int targetPosition;
    if (pawnColor == IntColor.WHITE) {
      targetPosition = endPosition - 16;
    } else {
      assert pawnColor == IntColor.BLACK;

      targetPosition = endPosition + 16;
    }

    // Remove the captured pawn
    int target = remove(targetPosition, true);
    assert IntMove.getTarget(move) != IntChessman.NOPIECE;
    assert IntChessman.getChessman(target) == IntChessman.PAWN;
    assert IntChessman.getColor(target) == IntColor.switchColor(pawnColor);
    captureHistory[captureHistorySize++] = target;

    // Update the capture square
    // This is the end position of the move, not the en passant position
    captureSquare = endPosition;

    // Update en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      zobristCode ^= zobristEnPassant[enPassantSquare];
      enPassantSquare = IntPosition.NOPOSITION;
    }

    // Update half move clock
    halfMoveClock = 0;
  }

  private void undoMoveEnPassant(int move) {
    // Move the pawn
    int endPosition = IntMove.getEnd(move);
    int pawn = move(endPosition, IntMove.getStart(move), false);

    // Calculate the en passant position
    int targetPosition;
    if (IntChessman.getColor(pawn) == IntColor.WHITE) {
      targetPosition = endPosition - 16;
    } else {
      assert IntChessman.getColor(pawn) == IntColor.BLACK;

      targetPosition = endPosition + 16;
    }

    // Restore the captured pawn
    put(captureHistory[--captureHistorySize], targetPosition, false);
  }

  private void makeMoveNull() {
    // Update the capture square
    captureSquare = IntPosition.NOPOSITION;

    // Update en passant
    if (enPassantSquare != IntPosition.NOPOSITION) {
      zobristCode ^= zobristEnPassant[enPassantSquare];
      enPassantSquare = IntPosition.NOPOSITION;
    }

    // Update half move clock
    halfMoveClock++;
  }

  /**
   * Adds the position to the list.
   *
   * @param position the position.
   * @param list     the position list.
   */
  private void addPosition(int position, PositionList list) {
    assert (position & 0x88) == 0;
    assert list != null;
    assert list.size >= 0 && list.size < PositionList.MAXSIZE;

    // Iterate over the list from the end
    int j = list.size;
    for (int i = list.size - 1; i >= 0; i--) {
      assert list.position[i] != position;
      if (list.position[i] > position) {
        list.position[j] = list.position[i];
        j--;
      } else {
        break;
      }
    }
    list.position[j] = position;
    list.size++;

    assert list.size > 0 && list.size <= PositionList.MAXSIZE;
  }

  /**
   * Removes the position from the list.
   *
   * @param position the position.
   * @param list     the position list.
   */
  private void removePosition(int position, PositionList list) {
    assert (position & 0x88) == 0;
    assert list != null;
    assert list.size > 0 && list.size <= PositionList.MAXSIZE;

    // Iterate over the list from the beginning
    int j = 0;
    for (int i = 0; i < list.size; i++) {
      if (list.position[i] != position) {
        list.position[j] = list.position[i];
        j++;
      }
    }

    list.size--;

    assert list.size >= 0 && list.size < PositionList.MAXSIZE;
  }

  public String toString() {
    return getBoard().toString();
  }

}
