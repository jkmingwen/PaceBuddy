package com.dobi.walkingsynth.view;

import android.util.Log;

import java.util.*;
import java.text.DecimalFormat;

public class Synchro {
    private double stepTime;
    private double firstBeatBeforeStep;
    private double firstBeatAfterStep;
    
    public Synchro(double stepTime,double firstBeatBeforeStep, double firstBeatAfterStep) {
        this.stepTime = stepTime;
        this.firstBeatBeforeStep = firstBeatBeforeStep;
        this.firstBeatAfterStep = firstBeatAfterStep;
    }
    
    public double getStepTime(){
        return stepTime;
    }
    public double getFirstBeatBeforeStep() {
        return firstBeatBeforeStep;
    }
    public double getFirstBeatAfterStep() {
        return firstBeatAfterStep;
    }

    // calculates a score out of 100 the measure of how far a step is from the closest beat
    public double calculateScore() {
        double st = stepTime;
        double bef = firstBeatBeforeStep;
        double aft = firstBeatAfterStep;
        // calculates phase angle 
        double phaseAngle = 360 * ((st - bef) / (aft - bef));
        double output = 0.0;

        // calculation of score is based on the nearest beat, so it make sense to 
        // put it on a -180 to 180 degrees plane instead of a 0 to 360 degrees
        // it is a measure of how far the step is to the furthest beat
        if (phaseAngle > 180) {
            phaseAngle = phaseAngle - 360;
        }
        // A negative phase angle indicated the step occurred before the closest beat, 
        // while a positive phase angle indicates the step occurred after the closest beat

        if (phaseAngle == 180) {
            // in the middle of 2 beats
            output = 0;
        } else if (phaseAngle > 0) {
            // step happens after the closest beat
            output = 1 - phaseAngle/180;
        } else if (phaseAngle < 0) {
            output = 1 - (-1*phaseAngle)/180;
        } else {
            //phaseAngle == 0
            // means step happens on the beat itself
            output = 1;
        }

      DecimalFormat df = new DecimalFormat("#.##");
      Log.println(Log.DEBUG, "stepTime: ", Double.toString(stepTime));
        Log.println(Log.DEBUG, "beforeTime: ", Double.toString(firstBeatBeforeStep));
        Log.println(Log.DEBUG, "afterTime: ", Double.toString(firstBeatAfterStep));
      output = Double.valueOf(df.format(output*100));

      
        return output;

    }

    public static void main(String[] args) {
        // write a short driver code on hows it supposed to be like
        Synchro sc = new Synchro(0.5,0.4,0.6);
        Synchro sc1 = new Synchro(0.45,0.4,0.6);
        Synchro sc2 = new Synchro(0.55,0.4,0.6);
        Synchro sc3 = new Synchro(0.4123212322,0.4,0.6);
        Synchro sc4 = new Synchro(0.489,0.4,0.6);
        System.out.println(sc.calculateScore());
        System.out.println(sc1.calculateScore());
        System.out.println(sc2.calculateScore());
        System.out.println(sc3.calculateScore());
        System.out.println(sc4.calculateScore());


        // how to integrate into our app:
        // so we will have all the timestamps of the music beats
        // and timestamps of the feetfall
        // This usually works by calculate the average of scores over a certain period of time

        // when detect feetfall timestamp, record with the closest music beats
        // create a Synchro object, then add to the ArrayList of Synchro
        // get the starting and ending time of the period u wish to observe,
        // find the range of index of Synchro objects,
        // get the average of number of score in the ArrayList
        // display score
        // 
        // or upon each step, create the synchro object,
        // find the average everytime at feetfall
        // maybe can sleep every 3 seconds so data wont update so fast


    }

}