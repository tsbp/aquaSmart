package com.voodoo.aquasmart;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.TimePicker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.voodoo.aquasmart.UDPProcessor.OnReceiveListener;

import static com.voodoo.aquasmart.IPHelper.getBroadcastIP4AsBytes;
import static com.voodoo.aquasmart.IPHelper.getLocalIP4AsBytes;

public class MainAqua extends Activity implements OnReceiveListener{

    TextView tvTemp, tvTempS, tvDate, tvDayTime, tvNightTime, tvDayLight, tvNightLight;
    ImageView imgDayPeriod;

    String[] names = { "Компрессор", "Фильтр"};
    String[] start = { "12:00", "14:24"};
    String[] stop = { "16:00", "22:24"};

    private final String ATTRIBUTE_TITLE = "title";
    private final String ATTRIBUTE_TIME_START = "tStart";
    private final String ATTRIBUTE_TIME_STOP = "tStop";

    private final byte CMD_GET_STATE     = (byte)0x10;
    private final byte CMD_GET_STATE_ANS = (byte)0x11;

    private final byte CMD_GET_CFG   = (byte)0x20;

    private Timer mTimer;
    private MyTimerTask mMyTimerTask;

    public static UDPProcessor udpProcessor ;
    InetAddress deviceIP = null, broadcastIP;

    ListView lvMain;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_aqua);

        tvDate = (TextView) findViewById(R.id.tvDate);
        imgDayPeriod = (ImageView) findViewById(R.id.imgPeroid);

        udpProcessor = new UDPProcessor(7373);
        udpProcessor.setOnReceiveListener(this);
        udpProcessor.start();

        byte [] bcIP = getBroadcastIP4AsBytes();
        try {
            broadcastIP = InetAddress.getByAddress(bcIP);
        }
        catch (UnknownHostException e){}

        sendCmd(CMD_GET_STATE, broadcastIP);

        // находим список
        lvMain = (ListView) findViewById(R.id.lvPeripherial);

        listUpdate();
        //==========================================================
        lvMain.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                dialog_peripherial(position);
            }
        });

        tvTemp = (TextView) findViewById(R.id.tvTemp);
        tvTempS = (TextView) findViewById(R.id.tvTempS);
        tvTemp.setText("23.0\u00b0");
        tvTempS.setText("22.5\u00b0");

        tvTempS.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Dialog_temperature();
            }
        });

        LinearLayout llDay = (LinearLayout) findViewById(R.id.llDay);
        LinearLayout llNight = (LinearLayout) findViewById(R.id.llNight);

        llDay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Dialog_period(true);
            }
        });

        llNight.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Dialog_period(false);
            }
        });

        tvDayTime = (TextView) findViewById(R.id.tvDayTime);
        tvNightTime = (TextView) findViewById(R.id.tvNightTime);
        tvDayLight = (TextView) findViewById(R.id.tvLightDay);
        tvNightLight = (TextView) findViewById(R.id.tvLightNight);

        mTimer = new Timer();
        mMyTimerTask = new MyTimerTask();
        mTimer.schedule(mMyTimerTask, 1000, 1000);

        Button wifi = (Button) findViewById(R.id.btnWifi);
        //================================================
        wifi.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog_wifi();
            }
        });

        Button btnLoad = (Button) findViewById(R.id.btnLoad);
        //================================================
        btnLoad.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(deviceIP != null)
                    sendCmd(CMD_GET_CFG, deviceIP);
                else
                    sendCmd(CMD_GET_STATE, broadcastIP);
            }
        });
    }
    //==============================================================================================
    void sendCmd(byte aCmd, InetAddress aIP)
    {
        byte[] pack = new byte[1];
        switch (aCmd) {

            case CMD_GET_STATE:
                pack = new byte[1];
                pack[0] = (byte) 0x10;
                break;

            case CMD_GET_CFG:
                break;
        }
        if(aIP != null) udpSend(pack, aIP);
    }
    //==============================================================================================
    byte crcCalc(byte [] aBuf, int aL)
    {
        int sum = 0;
        for(int i = 0; i < aL; i++)
            sum += (int) (aBuf[i]) & 0xff;
       return  (byte) ((byte)(sum >> 8) + (byte)( sum &((byte) 0xff)));
    }
    //==============================================================================================
    byte[] formUdpPackage(byte [] aByte)
    {
        byte [] p = new byte[aByte.length + 2];
        p[0] = (byte) aByte.length;

        for(int i = 0; i < aByte.length; i++)
            p[i+1] = aByte[i];
        // add crc
        p[p.length - 1] = crcCalc(p, p.length - 1);
        return p;
    }
    //==============================================================================================
    void udpSend(byte[] aByte, InetAddress ip)
    {
        formUdpPackage(aByte);
        DataFrame df = new DataFrame(formUdpPackage(aByte));
        udpProcessor.send(ip,df);
    }
    //==============================================================================================
    public void onFrameReceived(InetAddress ip, IDataFrame frame)
    {
        byte[] in = frame.getFrameData();
        if(crcCalc(in, in[0]) == in[in.length - 1])
        {
            switch (in[1])
            {
                case CMD_GET_STATE_ANS:
                    if(deviceIP == null) deviceIP = ip;

                    tvDate.setText(in[2] + ":" + in[3] + ":" + in[4]);
                    //tvTemp.setText();
                    int cur = in[2] * 60 + in[3];
                    int day = getMinutes(tvDayTime.getText().toString());
                    int nig = getMinutes(tvNightTime.getText().toString());
                    if      (cur >= day && cur >= nig)   imgDayPeriod.setImageResource(R.drawable.moon);
                    else if (cur >= day)                 imgDayPeriod.setImageResource(R.drawable.sun);
                    else                                 imgDayPeriod.setImageResource(R.drawable.moon);
                    break;

                case CMD_GET_CFG:
                    break;
            }
        }
    }
    //==============================================================================================
    void dialog_wifi() {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.dialog_wifi, (ViewGroup) findViewById(R.id.rl));

        popDialog.setIcon(R.drawable.wifi_small);
        popDialog.setTitle("Установки wifi");
        popDialog.setView(Viewlayout);

        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        popDialog.create();
        popDialog.show();
    }
    //==============================================================================================
    void listUpdate()
    {
        ArrayList<Map<String, Object>> data = new ArrayList<>(
                names.length);
        Map<String, Object> m;
        for (int i = 0; i < names.length; i++) {
            m = new HashMap<>();
            m.put(ATTRIBUTE_TITLE,  names[i]);

            m.put(ATTRIBUTE_TIME_START, start[i]);
            m.put(ATTRIBUTE_TIME_STOP, stop[i]);
            data.add(m);
        }
        String[] from = {ATTRIBUTE_TITLE, ATTRIBUTE_TIME_START, ATTRIBUTE_TIME_STOP};
        int[] to = {R.id.tvTitle, R.id.tvStart, R.id.tvStop};
        SimpleAdapter sAdapter = new SimpleAdapter(this, data, R.layout.peripherial, from, to);
        lvMain.setAdapter(sAdapter);
    }
    //==============================================================================================
    public void dialog_peripherial(final int aSelected)
    {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.periph_set, (ViewGroup) findViewById(R.id.rl));

        final TextView pStart = (TextView)Viewlayout.findViewById(R.id.tvStart);
        final TextView pStop  = (TextView)Viewlayout.findViewById(R.id.tvStop);

        pStart.setText(start[aSelected]);

        pStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int hour, minute;
                hour = Integer.valueOf(pStart.getText().toString().substring(0, 2));
                minute = Integer.valueOf(pStart.getText().toString().substring(3));

                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(MainAqua.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        start[aSelected] = String.format("%02d",selectedHour) + ":" + String.format("%02d",selectedMinute);
                        pStart.setText(start[aSelected]);
                    }
                }, hour, minute, true);//Yes 24 hour time
                mTimePicker.setTitle("Select Time start");
                mTimePicker.show();
            }
        });

        pStop.setText(stop[aSelected]);

        pStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int hour, minute;
                hour = Integer.valueOf(pStop.getText().toString().substring(0, 2));
                minute = Integer.valueOf(pStop.getText().toString().substring(3));

                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(MainAqua.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        stop[aSelected] = String.format("%02d",selectedHour) + ":" + String.format("%02d",selectedMinute);
                        pStop.setText(stop[aSelected]);
                    }
                }, hour, minute, true);//Yes 24 hour time
                mTimePicker.setTitle("Select Time stop");
                mTimePicker.show();
            }
        });

        if(aSelected == 0)  popDialog.setIcon(R.drawable.air);
        else                popDialog.setIcon(R.drawable.filter);
        popDialog.setTitle(names[aSelected]);
        popDialog.setView(Viewlayout);

        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        listUpdate();
                        dialog.dismiss();
                    }
                });
        popDialog.create();
        popDialog.show();
    }

    //==============================================================================================
    public void Dialog_period(boolean aP)
    {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.activity_peroid_config, (ViewGroup) findViewById(R.id.rl));

        final TextView pTime = (TextView)Viewlayout.findViewById(R.id.setTime);

        if(aP) pTime.setText(tvDayTime.getText());
        else   pTime.setText(tvNightTime.getText());

        final boolean a = aP;
        pTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int hour, minute;
                if(a) {
                    hour = Integer.valueOf(tvDayTime.getText().toString().substring(0, 2));
                    minute = Integer.valueOf(tvDayTime.getText().toString().substring(3));
                }
                else
                {
                    hour = Integer.valueOf(tvNightTime.getText().toString().substring(0, 2));
                    minute = Integer.valueOf(tvNightTime.getText().toString().substring(3));
                }

                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(MainAqua.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        pTime.setText(String.format("%02d",selectedHour) + ":" + String.format("%02d",selectedMinute));
                    }
                }, hour, minute, true);//Yes 24 hour time
                mTimePicker.setTitle("Select Time");
                mTimePicker.show();
            }
        });

        final TextView pTemp = (TextView)Viewlayout.findViewById(R.id.setTemp);

        if(aP)         pTemp.setText(tvDayLight.getText());
        else  pTemp.setText(tvNightLight.getText());


        if (aP) {popDialog.setTitle("Установка дня");  popDialog.setIcon(R.drawable.day);}
        else    {popDialog.setTitle("Установка ночи"); popDialog.setIcon(R.drawable.night);}
        popDialog.setView(Viewlayout);
//
        SeekBar seek = (SeekBar) Viewlayout.findViewById(R.id.seekBarTemp);
        String s = (pTemp.getText().toString());
        s = s.substring(0, s.length()- 1);
        seek.setProgress(Integer.valueOf(s));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){

                pTemp.setText( progress + "%" );
            }
            public void onStartTrackingTouch(SeekBar arg0) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if(a) {
                            tvDayTime.setText(pTime.getText());
                            tvDayLight.setText(pTemp.getText());
                        }
                        else
                        {
                            tvNightTime.setText(pTime.getText());
                            tvNightLight.setText(pTemp.getText());
                        }
                        dialog.dismiss();
                    }
                });
        popDialog.create();
        popDialog.show();
    }
    //==============================================================================================
    public void Dialog_temperature()
    {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.temp_set, (ViewGroup) findViewById(R.id.layout_ust));

        popDialog.setIcon(android.R.drawable.star_big_on);
        popDialog.setTitle("Установка температуры");
        popDialog.setView(Viewlayout);

        final TextView delta = (TextView)Viewlayout.findViewById(R.id.delta_value);
        delta.setText(tvTempS.getText());

        SeekBar seek = (SeekBar) Viewlayout.findViewById(R.id.seekBar);
        String s = tvTempS.getText().toString();
        s = s.substring(0,s.length() - 1);
        seek.setProgress((int) (((Float.valueOf(s)) - 19) * 10));
//        seek.setProgress(deltaValue);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                String s = String.format("%2.1f", (0.1 * progress + 19));
                s = s.replace(',','.');
                delta.setText( s + "\u00b0");
            }
            public void onStartTrackingTouch(SeekBar arg0) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        tvTempS.setText(delta.getText());
                        dialog.dismiss();
                    }
                });
        popDialog.create();
        popDialog.show();
    }

    //==============================================================================================
    int getMinutes(String aStr)
    {
        int minutes = 60 * Integer.valueOf(aStr.substring(0, 2));
        minutes    +=      Integer.valueOf((aStr).substring(3));
        return minutes;
    }
    //==============================================================================================
    class MyTimerTask extends TimerTask {

        @Override
        public void run() {
//            Calendar calendar = Calendar.getInstance();
//            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
//                    "HH:mm:ss", Locale.getDefault());
//            final String strDate = simpleDateFormat.format(calendar.getTime());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sendCmd(CMD_GET_STATE, deviceIP);
                }
            });
        }
    }
    //==============================================================================================
    @Override
    protected void onDestroy() {
        super.onDestroy();
        udpProcessor.stop();
    }

}
