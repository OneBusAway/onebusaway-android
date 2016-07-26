/*
 * Copyright 2012 University of South Florida
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package org.onebusaway.android.directions.model;

import java.util.ArrayList;

/**
 * @author Khoa Tran
 */

public class Direction {

    private int icon;

    private int directionIndex;

    private CharSequence directionText;

    private CharSequence service;

    private CharSequence agency;

    private CharSequence placeAndHeadsign;

    private CharSequence extra;

    private CharSequence oldTime;

    private CharSequence newTime = null;

    private boolean isTransit = false;

    private ArrayList<Direction> subDirections = null;

    private boolean realTimeInfo = false;

    public Direction() {
        super();
    }

    public Direction(int icon, CharSequence service, CharSequence placeAndHeadsign, CharSequence oldTime,
                     CharSequence newTime, boolean isTransit) {
        super();
        this.setIcon(icon);
        this.service = service;
        this.placeAndHeadsign = placeAndHeadsign;
        this.oldTime = oldTime;
        this.newTime = newTime;
        this.isTransit = isTransit;
    }

    public Direction(int icon, CharSequence directionText) {
        super();
        this.setIcon(icon);
        this.directionText = directionText;
    }

    /**
     * @return the icon
     */
    public int getIcon() {
        return icon;
    }

    /**
     * @param icon the icon to set
     */
    public void setIcon(int icon) {
        this.icon = icon;
    }

    public CharSequence getService() {
        return service;
    }

    public void setService(CharSequence service) {
        this.service = service;
    }

    /**
     * @return the subDirections
     */
    public ArrayList<Direction> getSubDirections() {
        return subDirections;
    }

    /**
     * @param subDirections the subDirections to set
     */
    public void setSubDirections(ArrayList<Direction> subDirections) {
        this.subDirections = subDirections;
    }

    public int getDirectionIndex() {
        return directionIndex;
    }

    public void setDirectionIndex(int directionIndex) {
        this.directionIndex = directionIndex;
    }

    public CharSequence getOldTime() {
        return oldTime;
    }

    public void setOldTime(CharSequence oldTime) {
        this.oldTime = oldTime;
    }

    public CharSequence getNewTime() {
        return newTime;
    }

    public void setNewTime(CharSequence newTime) {
        this.newTime = newTime;
    }

    public CharSequence getPlaceAndHeadsign() {
        return placeAndHeadsign;
    }

    public void setPlaceAndHeadsign(CharSequence placeAndHeadsign) {
        this.placeAndHeadsign = placeAndHeadsign;
    }

    public boolean isTransit() {
        return isTransit;
    }

    public void setTransit(boolean isTransit) {
        this.isTransit = isTransit;
    }

    public boolean isRealTimeInfo() {
        return realTimeInfo;
    }

    public void setRealTimeInfo(boolean realTimeInfo) {
        this.realTimeInfo = realTimeInfo;
    }

    public CharSequence getDirectionText() {
        return directionText;
    }

    public void setDirectionText(CharSequence directionText) {
        this.directionText = directionText;
    }

    public CharSequence getAgency() {
        return agency;
    }

    public void setAgency(CharSequence agency) {
        this.agency = agency;
    }

    public CharSequence getExtra() {
        return extra;
    }

    public void setExtra(CharSequence extra) {
        this.extra = extra;
    }
}
