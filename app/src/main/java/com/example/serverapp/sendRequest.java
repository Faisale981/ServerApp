package com.example.serverapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class sendRequest extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    public static final String pdu_type = "pdus";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Toast.makeText(context, "serverApp", Toast.LENGTH_LONG).show();
        if (intent.getAction().equals(SMS_RECEIVED)) {
        }          // Get the SMS message.
        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs;
        String strMessage = "";
        String format = bundle.getString("format");
        // Retrieve the SMS message received.
        Object[] pdus = (Object[]) bundle.get(pdu_type);
        if (pdus != null) {
            // Check the Android version.
            boolean isVersionM =
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
            // Fill the msgs array.
            msgs = new SmsMessage[pdus.length];
            for (int i = 0; i < msgs.length; i++) {
                // Check Android version and use appropriate createFromPdu.
                if (isVersionM) {
                    // If Android version M or newer:
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                } else {
                    // If Android version L or older:
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }
                // Build the message to show.
                // strMessage += "SMS from " + msgs[i].getOriginatingAddress();
                strMessage = msgs[i].getMessageBody();
                // Log and display the SMS message.
                //           Log.d(TAG, "onReceive: " + strMessage);
                //if (msgs[i].getOriginatingAddress().equals("+92 321 4173482")) {
                //  Toast.makeText(context, strMessage, Toast.LENGTH_LONG).show();
                // this.setResultCode(Telephony.Sms.Intents.RESULT_SMS_HANDLED);
                // abortBroadcast();

                if (strMessage.substring(0, 3).equals("C1C")) {
                    //Working on Sms
                    String origMessage = strMessage.substring(3);
                    String msgsPart[] = strMessage.split("#_#");
                    //0 -> Latitude 1 -> Longitude 2->U-ID
                    double lat = Double.parseDouble(msgsPart[0]);
                    double lng = Double.parseDouble(msgsPart[1]);
                    final String u_id = msgsPart[2];
                    final String contactNo = msgs[i].getOriginatingAddress();
                    final DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference()
                            .child("CustomerRequests");
                    databaseReference.child(u_id).child("BookedOffline").setValue(true);
                    databaseReference.child(u_id).child("sendMessageNo").setValue(contactNo);
                    databaseReference.child(u_id).child("0").setValue(lat);
                    databaseReference.child(u_id).child("1").setValue(lng);

                    final DatabaseReference databaseReference1 = FirebaseDatabase.getInstance().getReference().child("DriversAvailable");
                    GeoFire geoFire = new GeoFire(databaseReference1);
                    int radius = 20;
                    GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(lat, lng), radius);
                    geoQuery.removeAllListeners();
                    geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                        @Override
                        public void onKeyEntered(String key, GeoLocation location) {
                            FirebaseDatabase.getInstance().getReference().child("DriversAvailable").child(key).removeValue();
                            DatabaseReference dr = FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers");
                            dr = dr.child(key);
                            dr.child("UserID").setValue(u_id);
                            dr.child("isDriverConfirmed").setValue(false);
                            final String id = key;
                            dr = FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers");
                            dr.child(key).addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.hasChild("isDriverConfirmed")) {
                                        if (dataSnapshot.child("isDriverConfirmed").getValue().equals(true)) {
                                            //driverFound=true;
                                            //removeGeoQueryListeners();
                                            //pd.dismiss();
                                            //sendMessageTakingDetailsOfDriver

                                            sendDataToPassenger(id, contactNo, context);
                                            FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers").
                                                    child(id).removeValue();

                                        } else {
                                            FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers").child(id).removeValue();
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {

                                }
                            });
                            final Timer t = new Timer();
                            t.schedule(new TimerTask() {
                                public void run() {
                                    // when the task active then close the dialog
                                    t.cancel(); // also just top the timer thread, otherwise, you may receive a crash report
                                }
                            }, 8000);
                        }


                        @Override
                        public void onKeyExited(String key) {
                        }

                        @Override
                        public void onKeyMoved(String key, GeoLocation location) {
                        }

                        @Override
                        public void onGeoQueryReady() {

                        }

                        @Override
                        public void onGeoQueryError(DatabaseError error) {
                            //  Snackbar.make(,"Internet Error...",5000);
                        }
                    });
                }
            }
        }
    }

    private void sendDataToPassenger(String driver_id, final String mobileToSend, final Context context) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference()
                .child("Users").child("Drivers").child(driver_id);

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String name = dataSnapshot.child("name").getValue().toString().trim();
                String mobile = dataSnapshot.child("mobile").getValue().toString().trim();
                String messageToSend = "";
                messageToSend = "Driver's Name: " + name + "\n Contact No: " + mobile;
                ArrayList<String> msgStringArray = SmsManager.getDefault().divideMessage(messageToSend);
                SimUtil.sendMultipartTextSMS(context, 0, mobileToSend, null, msgStringArray, null, null);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
}