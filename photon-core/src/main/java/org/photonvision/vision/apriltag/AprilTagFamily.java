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

package org.photonvision.vision.apriltag;

public enum AprilTagFamily {
    Tag36h11,
    Tag25h9,
    Tag16h5,
    TagCircle21h7,
    TagCircle49h12,
    TagStandard41h12,
    TagStandard52h13,
    TagCustom48h11;

    public String getNativeName() {
        // We want to strip the leading kT and replace with "t"
        return this.name().replaceFirst("T", "t");
    }
}
