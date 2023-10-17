/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.common.util.numbers;

import org.opencv.core.Point;

public class DoubleCouple extends NumberCouple<Double> {
    public DoubleCouple() {
        super(0.0, 0.0);
    }

    public DoubleCouple(Number first, Number second) {
        super(first.doubleValue(), second.doubleValue());
    }

    public DoubleCouple(Double first, Double second) {
        super(first, second);
    }

    public DoubleCouple(final Point point) {
        super(point.x, point.y);
    }

    public Point toPoint() {
        return new Point(first, second);
    }

    public void fromPoint(final Point point) {
        this.first = point.x;
        this.second = point.y;
    }
}
