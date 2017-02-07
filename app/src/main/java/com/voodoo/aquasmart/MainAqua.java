package com.voodoo.aquasmart;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
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
    Button btnSave, btnSet;

    String[] names = { "Компрессор", "Фильтр", "Шаговик"};
    String[] titles_start = { "Старт", "Старт", "Eat 1"};
    String[] titles_end = { "Стоп", "Стоп", "Eat 2"};
    String[] start = { "12:00", "14:24", "07:35"};
    String[] stop = { "16:00", "22:24", "18:45"};

    private final String ATTRIBUTE_TITLE = "title";
    private final String ATTRIBUTE_TITLE_START = "title_start";
    private final String ATTRIBUTE_TITLE_END = "title_end";
    private final String ATTRIBUTE_TIME_START = "tStart";
    private final String ATTRIBUTE_TIME_STOP = "tStop";

    private final byte OK_ANS            = (byte)0xaa;

    private final byte CMD_GET_STATE     = (byte)0x10;
    private final byte CMD_GET_STATE_ANS = (byte)0x11;

    private final byte CMD_GET_CFG       = (byte)0x20;
    private final byte CMD_SET_CFG       = (byte)0x21;
    private final byte CMD_GET_CFG_ANS   = (byte)0x22;

    private final byte CMD_GET_WIFI      = (byte)0x30;
    private final byte CMD_SET_WIFI      = (byte)0x31;

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

        //listUpdate();
        //==========================================================
        lvMain.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                dialog_peripherial(position);
            }
        });

        tvTemp = (TextView) findViewById(R.id.tvTemp);
        tvTempS = (TextView) findViewById(R.id.tvTempS);

        tvTempS.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(cfgTrue)
                    Dialog_temperature();
            }
        });

        LinearLayout llDay = (LinearLayout) findViewById(R.id.llDay);
        LinearLayout llNight = (LinearLayout) findViewById(R.id.llNight);

        llDay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(cfgTrue)
                    Dialog_period(true);
            }
        });

        llNight.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(cfgTrue)
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

        Button btnSync = (Button) findViewById(R.id.btnLoad);
        //================================================
        btnSync.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(deviceIP != null)
                    sendCmd(CMD_GET_CFG, deviceIP);
                else
                    sendCmd(CMD_GET_STATE, broadcastIP);
            }
        });

        btnSave = (Button) findViewById(R.id.btnSave);
        //================================================
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //if(deviceIP != null)
                {
                    sendCmd(CMD_SET_CFG, deviceIP);
                }
            }
        });
        btnSet = (Button) findViewById(R.id.btnSet);
        //================================================
        btnSet.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //if(deviceIP != null)
                {
                    stepperDialog();
                }
            }
        });
    }
    //==============================================================================================
    void stepperDialog()
    {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.stepper, (ViewGroup) findViewById(R.id.rl));

        popDialog.setTitle("Настройки");
        popDialog.setView(Viewlayout);

        final EditText turns = (EditText) Viewlayout.findViewById(R.id.etTurns);
        turns.setText(stepperTurnsCnt + "");

        final EditText eatMin = (EditText) Viewlayout.findViewById(R.id.etEatMin);
        eatMin.setText(eatMinutes + "");

        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        stepperTurnsCnt = Integer.valueOf(turns.getText().toString());
                        eatMinutes      = Integer.valueOf(eatMin.getText().toString());
                        btnSave.setVisibility(View.VISIBLE);
                        dialog.dismiss();
                    }
                });
        popDialog.create();
        popDialog.show();
    }
    //==============================================================================================
    byte []  getCfgPack()
    {
        byte[]tmp = new byte[23];
        byte[]ttmp = new byte[2];

        tmp[0] = (byte)(CMD_SET_CFG);
        //==== set temperature ==========
        int a = (int)(Float.valueOf(tvTempS.getText().toString().replace("\u00b0","")) * 10);
        tmp[1] = (byte)(a & 0xff);
        tmp[2] = (byte)((a >> 8) & 0xff);
        //======== light configs ==========
        ttmp = getBytesfromTimeString(tvDayTime.getText().toString());
        tmp[3] = ttmp[0];
        tmp[4] = ttmp[1];
        tmp[5] = Byte.valueOf((tvDayLight.getText().toString()).replace("%",""));
        ttmp = getBytesfromTimeString(tvNightTime.getText().toString());
        tmp[6] = ttmp[0];
        tmp[7] = ttmp[1];
        tmp[8] = Byte.valueOf((tvNightLight.getText().toString()).replace("%",""));
        //======== peripherial configs ====
        for(int i = 0; i < names.length; i++)
        {
            ttmp = getBytesfromTimeString(start[i]);
            tmp[i*4 + 9] = ttmp[0];
            tmp[i*4 + 10] = ttmp[1];
            ttmp = getBytesfromTimeString(stop[i]);
            tmp[i*4 + 11] = ttmp[0];
            tmp[i*4 + 12] = ttmp[1];
        }
        tmp[21] = (byte) stepperTurnsCnt;
        tmp[22] = (byte) eatMinutes;
        return tmp;
    }
    //==============================================================================================
    byte [] getBytesfromTimeString(String aStr)
    {
        byte [] tmp = new byte[2];
        tmp[0] = Byte.valueOf(aStr.substring(0, 2));
        tmp[1] = Byte.valueOf((aStr).substring(3));
        return tmp;
    }
    //==============================================================================================
    void sendCmd(byte aCmd, InetAddress aIP)
    {
        byte[] pack = null;
        switch (aCmd) {

            case CMD_GET_STATE:
                pack = new byte[1];
                pack[0] = (byte) aCmd;
                break;

            case CMD_GET_CFG:
                pack = new byte[4];
                pack[0] = (byte) aCmd;
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                        "HH:mm:ss", Locale.getDefault());
                String strDate = simpleDateFormat.format(calendar.getTime());
                pack[1] =  Byte.valueOf(strDate.substring(0,2));
                pack[2] =  Byte.valueOf(strDate.substring(3,5));
                pack[3] =  Byte.valueOf(strDate.substring(6));
                break;

            case CMD_SET_CFG:
                pack = getCfgPack();
                break;

            case CMD_SET_WIFI:
                byte [] tmp = wCfgStr.getBytes();
                pack = new byte[wCfgStr.length() + 1];
                pack[0] = (byte) aCmd;
                for(int i = 0; i < wCfgStr.length(); i++)
                    pack[i + 1] = tmp[i];
                break;
        }
        if(aIP != null && pack != null) udpSend(pack, aIP);
    }
    //==============================================================================================
    byte crcCalc(byte [] aBuf, int aL)
    {
        short sum = 0;
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
    boolean cfgTrue = false;
    int stepperTurnsCnt, eatMinutes;
    //==============================================================================================
    public void onFrameReceived(InetAddress ip, IDataFrame frame)
    {
        byte[] in = frame.getFrameData();
        if(crcCalc(in, in[0]) == in[in.length - 1])
        {
            switch (in[1])
            {
                case OK_ANS:
                    btnSave.setVisibility(View.INVISIBLE);
                    break;

                case CMD_GET_STATE_ANS:
                    if(deviceIP == null) deviceIP = ip;

                    String e = "", ee = "";
                    if(in[3] < 10) e = "0";
                    if(in[4] < 10) ee = "0";
                    tvDate.setText(in[2] + ":" + e + in[3] + ":" + ee + in[4]);
                    short tmp = (short)((in[5] & 0xff) | ((in[6] &0xff) << 8));
                    tvTemp.setText((float)tmp/10 + "°");
                    if(in[7] > 0) imgDayPeriod.setImageResource(R.drawable.sun);
                    else          imgDayPeriod.setImageResource(R.drawable.moon);
                    break;

                case CMD_GET_CFG_ANS: {
                    short a = (short) ((in[2] & 0xff) | ((in[3] & 0xff) << 8));
                    tvTempS.setText((float) a / 10 + "°");

                    tvDayTime.setText(getTimeString(in[4], in[5]));
                    tvDayLight.setText(in[6] + "%");
                    tvNightTime.setText(getTimeString(in[7], in[8]));
                    tvNightLight.setText(in[9] + "%");

                    for(int i = 0; i < 3; i++)
                    {
                        start[i] = getTimeString(in[i * 4 + 10],  in[i * 4 + 11]);
                        stop[i]  = getTimeString(in[i * 4 + 12],  in[i * 4 + 13]);
                    }
                    stepperTurnsCnt = (in[22] & 0xff);
                    eatMinutes = (in[23] & 0xff);
                    listUpdate();
                    cfgTrue = true;
                    btnSave.setVisibility(View.INVISIBLE);
                    btnSet.setVisibility(View.VISIBLE);
                }
                    break;
            }
        }
    }
    //==============================================================================================
    String getTimeString(byte aHour, byte aMinute)
    {
        String a = "", b = "";
        if(aHour < 10) a = "0";
        if(aMinute < 10) b = "0";
        return a + aHour + ":" + b + aMinute;
    }
    //==============================================================================================
    private String[] wifiMode= {"NULL_MODE","STATION_MODE","SOFTAP_MODE","STATIONAP_MODE"};
    private String[] wifiSecurityMode = {"AUTH_OPEN","AUTH_WEP","AUTH_WPA_PSK","AUTH_WPA2_PSK","AUTH_WPA_WPA2_PSK","AUTH_MAX"};
    byte wMode, wSecur;
    String wCfgStr;
    //==============================================================================================
    void dialog_wifi() {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.dialog_wifi, (ViewGroup) findViewById(R.id.rl));

        popDialog.setIcon(R.drawable.wifi_small);
        popDialog.setTitle("Установки wifi");
        popDialog.setView(Viewlayout);

        final EditText ssid = (EditText) Viewlayout.findViewById(R.id.etSSID);
        ssid.clearFocus();
        final EditText ssidPass = (EditText) Viewlayout.findViewById(R.id.etSSIDPASS);
        ssidPass.clearFocus();

        ArrayAdapter<String> adapterW = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, wifiMode);
        ArrayAdapter<String> adapterS = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, wifiSecurityMode);

        Spinner spinnerW = (Spinner) Viewlayout.findViewById(R.id.spinwWifiMode);
        Spinner spinnerS = (Spinner) Viewlayout.findViewById(R.id.spinSecur);

        spinnerW.setAdapter(adapterW);
        spinnerS.setAdapter(adapterS);

        byte[]cfg = loadConfig();
        if(cfg.length > 0)
        {

            spinnerS.setSelection((int)cfg[1]);
            spinnerW.setSelection((int)cfg[0]);
            String str = new String(cfg);
            ssid.setText(str.substring(2,str.indexOf('$')));
            ssidPass.setText(str.substring(str.indexOf('$') + 1, str.length()));
        }

        spinnerS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                wSecur = (byte) position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        spinnerW.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                wMode = (byte) position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        wCfgStr = (char)wMode + "" + (char)wSecur + ssid.getText().toString() + "$" + ssidPass.getText().toString();
                        saveConfig(wCfgStr);
                        sendCmd(CMD_SET_WIFI, deviceIP);
                        dialog.dismiss();
                    }
                });
        popDialog.create();
        popDialog.show();
    }
    //==============================================================================================
    public static String configReference = "com.voodoo.aquasmart";
    //==============================================================================================
    void saveConfig(String aStr) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(configReference, aStr);

        editor.apply();
    }
    //==============================================================================================
    byte[] loadConfig() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String conf = sharedPreferences.getString(configReference, "") ;
        return conf.getBytes();
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

            m.put(ATTRIBUTE_TITLE_START, titles_start[i]);
            m.put(ATTRIBUTE_TITLE_END, titles_end[i]);
            m.put(ATTRIBUTE_TIME_START, start[i]);
            m.put(ATTRIBUTE_TIME_STOP, stop[i]);
            data.add(m);
        }
        String[] from = {ATTRIBUTE_TITLE, ATTRIBUTE_TITLE_START, ATTRIBUTE_TITLE_END, ATTRIBUTE_TIME_START, ATTRIBUTE_TIME_STOP};
        int[] to = {R.id.tvTitle, R.id.tvTitleStart, R.id.tvTitleStop, R.id.tvStart, R.id.tvStop};
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

        final TextView tStart = (TextView)Viewlayout.findViewById(R.id.tvTitleStartP);
        tStart.setText(titles_start[aSelected]);
        final TextView tStop  = (TextView)Viewlayout.findViewById(R.id.tvTitleStopP);
        tStop.setText(titles_end[aSelected]);

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
                        btnSave.setVisibility(View.VISIBLE);
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
                        btnSave.setVisibility(View.VISIBLE);
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
                        btnSave.setVisibility(View.VISIBLE);
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

                btnSave.setVisibility(View.VISIBLE);
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
                btnSave.setVisibility(View.VISIBLE);
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
        mTimer.cancel();
        cfgTrue = false;
    }

}
