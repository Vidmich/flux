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
package com.fluxchess.flux.move;

import com.fluxchess.flux.board.Attack;
import com.fluxchess.flux.board.Hex88Board;
import com.fluxchess.flux.board.IntChessman;
import com.fluxchess.flux.table.HistoryTable;
import com.fluxchess.flux.table.KillerTable;
import com.fluxchess.jcpi.models.GenericBoard;
import com.fluxchess.jcpi.models.IllegalNotationException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MoveGeneratorTest {

  private static final Logger LOG = LoggerFactory.getLogger(MoveGeneratorTest.class);

  private final TestPerftTable table = new TestPerftTable();

  @Test
  public void testSpecialPerft() {
    // Setup a new board from fen
    GenericBoard board;
    try {
      board = new GenericBoard("1k6/8/8/5pP1/4K1P1/8/8/8 w - f6");
      Hex88Board testBoard = new Hex88Board(board);
      new MoveSee(testBoard);
      table.increaseAge();

//          testBoard.makeMove(IntMove.createMove(IntMove.NORMAL, IntPosition.d2, IntPosition.c1, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN));
//          testBoard.makeMove(IntMove.createMove(IntMove.NORMAL, IntPosition.e7, IntPosition.d6, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN));
//          testBoard.makeMove(IntMove.createMove(IntMove.NORMAL, IntPosition.e1, IntPosition.d1, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN));
//          testBoard.makeMove(IntMove.createMove(IntMove.PAWNDOUBLE, IntPosition.c7, IntPosition.c5, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN, IntChessman.NOCHESSMAN));
      int result = miniMax(testBoard, new MoveGenerator(testBoard, new KillerTable(), new HistoryTable()), 5, 5);
      LOG.info(String.format("%d", result));
    } catch (IllegalNotationException e) {
      fail();
    }
  }

  @Test
  public void testPerft() {
    for (int i = 1; i < 4; i++) {
//      for (int i = 1; i < 7; i++) {
      try {
        BufferedReader file = null;
        try {
          file = new BufferedReader(new FileReader("perftsuite.epd"));
        } catch (FileNotFoundException e) {
          file = new BufferedReader(new FileReader("src/test/resources/perftsuite.epd"));
        }

        String line = file.readLine();
        while (line != null) {
          String[] tokens = line.split(";");

          // Setup a new board from fen
          GenericBoard board = new GenericBoard(tokens[0].trim());

          if (tokens.length > i) {
            String[] data = tokens[i].trim().split(" ");
            int depth = Integer.parseInt(data[0].substring(1));
            int nodesNumber = Integer.parseInt(data[1]);

            Hex88Board testBoard = new Hex88Board(board);
            new MoveSee(testBoard);
            table.increaseAge();

            long startTime = System.currentTimeMillis();
            int result = miniMax(testBoard, new MoveGenerator(testBoard, new KillerTable(), new HistoryTable()), depth, depth);
            long endTime = System.currentTimeMillis();
            LOG.info("Testing " + tokens[0].trim() + " depth " + depth + " with nodes number " + nodesNumber + ": " + String.format("%d", endTime - startTime));
            assertEquals(tokens[0].trim(), nodesNumber, result);
          }

          line = file.readLine();
        }
      } catch (IOException | IllegalNotationException e) {
        fail();
      }
    }
  }

  private int miniMax(Hex88Board board, MoveGenerator moveGenerator, int depth, int maxDepth) {
    if (depth == 0) {
      return 1;
    }

    int totalNodes = table.get(board.zobristCode);
    if (totalNodes > 0) {
      return totalNodes;
    }

    Attack attack = board.getAttack(board.activeColor);
    moveGenerator.initializeMain(attack, 0, IntMove.NOMOVE);

    int nodes = 0;
    int move = moveGenerator.getNextMove();
    while (move != IntMove.NOMOVE) {
      boolean isCheckingMove = board.isCheckingMove(move);
      GenericBoard oldBoard = board.getBoard();

      int captureSquare = board.captureSquare;
      board.makeMove(move);
      boolean isCheckingMoveReal = board.getAttack(board.activeColor).isCheck();
      assertEquals(oldBoard.toString() + ", " + IntMove.toGenericMove(move).toString(), isCheckingMoveReal, isCheckingMove);
      nodes = miniMax(board, moveGenerator, depth - 1, maxDepth);
      board.undoMove(move);
      assert captureSquare == board.captureSquare;

//          if (depth == maxDepth) {
//              System.out.println(IntMove.toGenericMove(move).toLongAlgebraicNotation() + ": " + nodes);
//          }
      totalNodes += nodes;
      move = moveGenerator.getNextMove();
    }

    moveGenerator.destroy();

    table.put(board.zobristCode, totalNodes);

    return totalNodes;
  }

  @Test
  public void testSpecialQuiescentCheckingMoves() {
    // Setup a new board from fen
    GenericBoard board;
    try {
      board = new GenericBoard("8/8/3K4/3Nn3/3nN3/4k3/8/8 b - - 0 1");
      Hex88Board testBoard = new Hex88Board(board);

      miniMaxQuiescentCheckingMoves(testBoard, new MoveGenerator(testBoard, new KillerTable(), new HistoryTable()), 3, 3);
    } catch (IllegalNotationException e) {
      fail();
    }
  }

  @Test
  public void testQuiescentCheckingMoves() {
    for (int i = 1; i < 3; i++) {
//      for (int i = 1; i < 7; i++) {
      try {
        BufferedReader file = null;
        try {
          file = new BufferedReader(new FileReader("perftsuite.epd"));
        } catch (FileNotFoundException e) {
          file = new BufferedReader(new FileReader("src/test/resources/perftsuite.epd"));
        }

        String line = file.readLine();
        while (line != null) {
          String[] tokens = line.split(";");

          // Setup a new board from fen
          GenericBoard board = new GenericBoard(tokens[0].trim());

          if (tokens.length > i) {
            String[] data = tokens[i].trim().split(" ");
            int depth = Integer.parseInt(data[0].substring(1));
            int nodesNumber = Integer.parseInt(data[1]);

            Hex88Board testBoard = new Hex88Board(board);
            new MoveSee(testBoard);

            LOG.info("Testing " + tokens[0].trim() + " depth " + depth + " with nodes number " + nodesNumber + ":");
            miniMaxQuiescentCheckingMoves(testBoard, new MoveGenerator(testBoard, new KillerTable(), new HistoryTable()), depth, depth);
          }

          line = file.readLine();
        }
      } catch (IOException | IllegalNotationException e) {
        fail();
      }
    }
  }

  private void miniMaxQuiescentCheckingMoves(Hex88Board board, MoveGenerator moveGenerator, int depth, int maxDepth) {
    if (depth == 0) {
      return;
    }

    Attack attack = board.getAttack(board.activeColor);

    // Get quiescent move list
    MoveList quiescentMoveList = new MoveList();
    moveGenerator.initializeQuiescent(attack, true);
    int move = moveGenerator.getNextMove();
    while (move != IntMove.NOMOVE) {
      quiescentMoveList.move[quiescentMoveList.tail++] = move;
      move = moveGenerator.getNextMove();
    }
    moveGenerator.destroy();

    // Do main moves and count
    MoveList mainMoveList = new MoveList();
    moveGenerator.initializeMain(attack, 0, IntMove.NOMOVE);
    move = moveGenerator.getNextMove();
    while (move != IntMove.NOMOVE) {
      if (!attack.isCheck()) {
        if ((IntMove.getTarget(move) != IntChessman.NOPIECE && isGoodCapture(move)) || (IntMove.getTarget(move) == IntChessman.NOPIECE && board.isCheckingMove(move)) && MoveSee.seeMove(move, IntMove.getChessmanColor(move)) >= 0) {
          board.makeMove(move);
          miniMaxQuiescentCheckingMoves(board, moveGenerator, depth - 1, maxDepth);
          board.undoMove(move);
          mainMoveList.move[mainMoveList.tail++] = move;
        }
      } else {
        board.makeMove(move);
        miniMaxQuiescentCheckingMoves(board, moveGenerator, depth - 1, maxDepth);
        board.undoMove(move);
        mainMoveList.move[mainMoveList.tail++] = move;
      }
      move = moveGenerator.getNextMove();
    }
    moveGenerator.destroy();

    assertEquals(printDifference(board, mainMoveList, quiescentMoveList), mainMoveList.getLength(), quiescentMoveList.getLength());
  }

  private String printDifference(Hex88Board board, MoveList main, MoveList quiescent) {
    String result = board.getBoard().toString() + "\n";

    new MoveRater(new HistoryTable()).rateFromMVVLVA(main);
    new MoveRater(new HistoryTable()).rateFromMVVLVA(quiescent);

    result += "     Main:";
    for (int i = 0; i < main.tail; i++) {
      result += " " + IntMove.toGenericMove(main.move[i]).toString();
    }
    result += "\n";

    result += "Quiescent:";
    for (int i = 0; i < quiescent.tail; i++) {
      result += " " + IntMove.toGenericMove(quiescent.move[i]).toString();
    }
    result += "\n";

    return result;
  }

  private static boolean isGoodCapture(int move) {
    if (IntMove.getType(move) == IntMove.PAWNPROMOTION) {
      return IntMove.getPromotion(move) == IntChessman.QUEEN;
    }

    int chessman = IntMove.getChessman(move);
    int target = IntMove.getTarget(move);

    assert chessman != IntChessman.NOPIECE;
    assert target != IntChessman.NOPIECE;

    if (IntChessman.getValueFromChessman(chessman) <= IntChessman.getValueFromChessman(target)) {
      return true;
    }

    return MoveSee.seeMove(move, IntMove.getChessmanColor(move)) >= 0;
  }

}
