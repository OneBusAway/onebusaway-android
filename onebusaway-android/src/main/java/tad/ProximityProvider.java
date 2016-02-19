package tad;

import android.location.Location;

/**
 * Created by azizmb on 2/18/16.
 */
public class ProximityProvider {
    //**  Proximity Listener variables **/
    private float radius = 160;  //Defines radius (in meters) for which the Proximity listener should be triggered (Default = 50)
    private float readyradius = 300; //Defines radius(in meters) for which the Proximity listener should trigger "Get Ready Alert"
    private boolean trigger = false;  //Defines whether the Proximity Listener has been triggered (true) or not (false)

    private Location secondtolastcoords = null;  //Tests distance from registered location w/ ProximityListener manually
    private Location lastcoords = null; //Coordinates of the final bus stop of the segment
    private Location firstcoords = null; //Coordinates of the first bus stop of the segment

    private float distance = -1;  //Actual known traveled distance loaded from segment object
    private float directdistance = -1; //Direct distance to second to last stop coords, used for radius detection
    private float endistance = -1; //Direct distance to last bus stop coords, used for segment navigation
    private boolean ready = false; //Has get ready alert been played?
    private boolean waitingForConfirm = false;
    private boolean m100_a,  m50_a,  m20_a,  m20_d,  m50_d,  m100_d = false;  //Varibales for handling arrival/departure from 2nd to last stop*/


    private int checkProximityAll(Location currentLocation) {
        if (!waitingForConfirm) {
            //re-calculate the distance to the final bus stop from the current location
            this.endistance = this.lastcoords.distanceTo(currentLocation);
            //re-calculate the distance to second to last bus stop from the current location
            this.directdistance = this.secondtolastcoords.distanceTo(currentLocation);
            System.out.println("Second to last stop coordinates: " + secondtolastcoords.getLatitude() + ", " + secondtolastcoords.getLongitude());
            try {
                //TODO: Update GUI to show distance to last stop

            } catch (Exception e3) {
                System.out.println("Warning - Could not set Distance...");
            }
            if (this.directdistance < 250) {
                //Fire proximity event for getting ready 100 meters prior to 2nd to last stop
                if (this.proximityEvent(1, -1)) {
                    System.out.println("-----Get ready!");
                    return 2; //Get ready alert played
                }
            }
            if (StopDetector(this.directdistance, 1, currentLocation.getSpeed())) {
                //   if (this.endistance < 160) {
                //Fire proximity event for getting off the bus

                if (this.proximityEvent(0, 0)) {
                    System.out.println("-----Get off the bus!");
                    return 1; // Get off bus alert played

                }

            }
            //check if near final stop and reset the 2nd to last stop detection variables.
            // This was replaced by the method resetVariablesAfterSegmentSwitching
    /*            if (StopDetector(this.endistance, 0, 0)) {
                    m100_a = false;
                    m50_a = false;
                    m20_a = false;
                    m20_d = false;
                    m50_d = false;
                    m100_d = false;
                }*/
        }
        return 0; //No alerts played.
    }

    public boolean proximityEvent(int selection, int t) {
        //*******************************************************************************************************************
        //* This function is fired by the ProximityListener when it detects that it is near a set of registered coordinates *
        //*******************************************************************************************************************
        // System.out.println("Fired proximityEvent() from ProximityListener object.");

        //NEW - if statement that encompases rest of method to check if navProvider has triggered navListener before for this coordinate
        if (selection == 0) {
            if (trigger == false) {
                trigger = true;
                System.out.println("Proximity Event fired");
                if (this.navProvider.hasMoreSegments()) {
                    if (t == 0) {
                        System.out.println("Alert 1 Screen showed to rider");
                        waitingForConfirm = true;
                        this.midlet.getDisplay().setCurrent(this.midlet.getAlert1());
                        System.out.println("Calling way point reached!");
                        System.err.println("Calling WayPoint Reached!");
                        this.navProvider.navlistener.waypointReached(this.lastcoords);
                        return true;
                    }
                    if (t == 1) {
                        try {
                            System.out.println("About to switch segment - from Proximity Event");
                            this.navProvider.navigateNextSegment(); //Uncomment this line to allow navigation on multiple segments within one service (chained segments)

                            this.ready = false; //Reset the "get ready" notification alert

                            this.trigger = false; //Reset the proximity notification alert

                        } catch (Exception e) {
                            System.out.println("Error in TADProximityListener.proximityEvent(): " + e);
                        }
                    }
                } else {
                    System.out.println("Got to last stop ");
                    if (t == 0) {
                        System.out.println("Alert 1 screen before last stop");
                        waitingForConfirm = true;
                        this.midlet.getDisplay().setCurrent(this.midlet.getAlert1());
                        System.out.println("Calling destination reached...");
                        System.err.println("calling destination reached!");
                        this.navProvider.navlistener.destinationReached();
                        return true;
                    }
                    if (t == 1) {
                        long time = System.currentTimeMillis();
                        System.out.println("Ending trip, going back to services");
                        try {
                            this.navProvider.service = null;
                            this.navProvider.segments = null;
                            this.navProvider.segmentIndex = 0;
                            System.out.println("Time setting variables to null: " + (System.currentTimeMillis() - time));
                            time = System.currentTimeMillis();
                            this.midlet.getDisplay().setCurrent(this.midlet.getChooseService1());
                            this.midlet.communicator.clientNotification();
                            System.out.println("Time showing GUI and notifying communicator: " + (System.currentTimeMillis() - time));
                            time = System.currentTimeMillis();
                            //Cancel any registration of the AVLServiceProvider & Listener
                            try {
                                midlet.avlProvider.setAVLDataListener(null, null, null, "", "");
                                //Reset AVLlistener, since the user is finished traveling and doesn't need AVL info
                                midlet.avlListener.reset();
                            } catch (Exception e) {
                                System.out.println("Error canceling the AVLServiceListener registration: " + e);
                            }
                            System.out.println("Time setting AVL Data Listener: " + (System.currentTimeMillis() - time));
                            time = System.currentTimeMillis();


                        } catch (Exception e) {
                            System.out.println("Error while sending distances to server");
                        }
                    }
                }
            }
        } else if (selection == 1) {
            if (ready == false) {
                ready = true;
                this.navProvider.navlistener.getReady();
                return true;
            }
        }

        return false;
    }

    public boolean StopDetector(float distance_d, int stop_type, float speed) {

            /* TODO: This comment was comented to avoid segment switching when the rider is
             * 20 meters away from the bus stop.
            if ((distance_d < 20) && (distance_d != -1) && stop_type == 0) {

                System.out.println("About to fire Proximity Event from Last Stop Detected");
                this.trigger = false;
                this.proximityEvent(0, 1);
                return true;

            } else */
        /*System.out.println("Detecting stop. distance_d=" +
                distance_d + ". stop_type=" + stop_type + " speed=" + speed);
        if  (stop_type == 1) {
                /* Check if the bus is on the second to last stop */
            if ((distance_d > 50) && (distance_d < 100) && (distance_d != -1) && !m100_a) {
                m100_a = true;
                System.out.println("Case 1: false");
                return false;
            }
            if ((distance_d > 20) && (distance_d < 50) && (distance_d != -1) && !m50_a) {
                m50_a = true;
                System.out.println("Case 2: false");
                return false;
            }
            if ((distance_d < 20) && (distance_d != -1) && !m20_a) {
                m20_a = true;
                if (speed > 15) {
                    System.out.println("Case 3: true");
                    return true;
                }
                System.out.println("Case 3: false");
                return false;
            }
            if ((distance_d < 20) && (distance_d != -1) && m20_a && !m20_d) {
                m20_d = true;
                if (speed < 10) {
                    System.out.println("Case 4: false");
                    return false;
                } else if (speed > 15) {
                    System.out.println("Case 4: true");
                    return true;
                }
            }
            if ((distance_d > 20) && (distance_d < 50) && (distance_d != -1) && !m50_d && (m20_d || m20_a)) {
                m50_d = true;
                System.out.println("Case 5: true");
                return true;
            }


        }
        return false;
    }
}
