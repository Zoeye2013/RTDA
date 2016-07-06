package fi.aalto.rtda;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

/** Java Class for One Calibration or One Measurement Record  **/
public class RecordItemClass{
    /** For time stamps **/
    private Calendar calendar;
    private SimpleDateFormat sdf;
    private String timeFullString;
    private long timeMilli;
    private String timeString;
    private String dateString;

    /** Signal Vectors of corresponding devices list **/
    private ArrayList<Short> signalVectors;

    public RecordItemClass(){
        signalVectors = new ArrayList<Short>();
        getSystemCurrentTime();
    }

    /** Get current time on phone **/
    public void getSystemCurrentTime(){
        calendar = Calendar.getInstance();
        sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        timeFullString = sdf.format(calendar.getTime());
        sdf = new SimpleDateFormat("hh:mm:ss");
        timeString = sdf.format(calendar.getTime());
        sdf = new SimpleDateFormat("yyyy-MM-dd");
        dateString = sdf.format(calendar.getTime());
        timeMilli = calendar.getTimeInMillis();
    }

    /** Set signalVectors with temporary rssis recorded in the background serivce **/
    public void setSignalVector(ArrayList<Short> list){
        signalVectors = (ArrayList<Short>)list.clone();
    }

    /** Time in yyyy-MM-dd hh:mm:ss format **/
    public String getTimeFullString(){
        return timeFullString;
    }

    /** Time in hh:mm:ss format **/
    public String getTimeString() {return  timeString;}

    /** Time in yyyy-MM-dd format **/
    public String getDateString() {return dateString;}

    /** Time in milliseconds **/
    public long getTimeMilli(){
        return timeMilli;
    }

    public ArrayList<Short> getSignalVectors(){
        return signalVectors;
    }
}
