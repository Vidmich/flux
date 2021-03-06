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

import com.fluxchess.jcpi.models.GenericScore;

public final class IntScore {

  public static final int NOSCORE = -9;

  public static final int EXACT = 0;
  public static final int ALPHA = 1;
  public static final int BETA = 2;

  public static final int MASK = 0x3;

  private IntScore() {
  }

  public static int valueOfScore(GenericScore value) {
    assert value != null;

    switch (value) {
      case EXACT:
        return EXACT;
      case ALPHA:
        return ALPHA;
      case BETA:
        return BETA;
      default:
        assert false : value;
        break;
    }

    throw new IllegalArgumentException();
  }

  public static GenericScore valueOfIntScore(int value) {
    assert value != NOSCORE;

    switch (value) {
      case EXACT:
        return GenericScore.EXACT;
      case ALPHA:
        return GenericScore.ALPHA;
      case BETA:
        return GenericScore.BETA;
      default:
        throw new IllegalArgumentException();
    }
  }

}
