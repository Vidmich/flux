/*
** Copyright 2007-2012 Phokham Nonava
**
** This file is part of Flux Chess.
**
** Flux Chess is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation, either version 3 of the License, or
** (at your option) any later version.
**
** Flux Chess is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with Flux Chess.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.fluxchess.table;

import com.fluxchess.board.Hex88Board;
import com.fluxchess.board.IntChessman;
import com.fluxchess.board.IntColor;
import com.fluxchess.board.IntPosition;
import com.fluxchess.move.IntMove;

/**
 * HistoryTable
 *
 * @author Phokham Nonava
 */
public final class HistoryTable {

	public static final int MAX_HISTORYVALUE = 65536;

	private final int[][] historyTable = new int[IntChessman.PIECE_VALUE_SIZE][Hex88Board.BOARDSIZE];

	/**
	 * Creates a new HistoryTable.
	 */
	public HistoryTable() {
	}
	
	/**
	 * Returns the number of hits for the move.
	 * 
	 * @param move the IntMove.
	 * @return the number of hits.
	 */
	public int get(int move) {
		assert move != IntMove.NOMOVE;
		
		int piece = IntMove.getChessmanPiece(move);
		int end = IntMove.getEnd(move);
		assert IntMove.getChessman(move) != IntChessman.NOPIECE;
		assert IntMove.getChessmanColor(move) != IntColor.NOCOLOR;
		assert (end & 0x88) == 0;
		
		return this.historyTable[piece][end];
	}

	/**
	 * Increment the number of hits for this move.
	 * 
	 * @param move the IntMove.
	 */
	public void add(int move, int depth) {
		assert move != IntMove.NOMOVE;
		
		int piece = IntMove.getChessmanPiece(move);
		int end = IntMove.getEnd(move);
		assert IntMove.getChessman(move) != IntChessman.NOPIECE;
		assert IntMove.getChessmanColor(move) != IntColor.NOCOLOR;
		assert (end & 0x88) == 0;
		
		this.historyTable[piece][end] += depth;
		
		if (this.historyTable[piece][end] >= MAX_HISTORYVALUE) {
			for (int pieceValue : IntChessman.pieceValues) {
				for (int positionValue : IntPosition.values) {
					this.historyTable[pieceValue][positionValue] /= 2;
				}
			}
		}
	}

}
