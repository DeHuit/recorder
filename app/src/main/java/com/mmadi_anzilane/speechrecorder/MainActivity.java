package com.mmadi_anzilane.speechrecorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity {


  //mes composants graphique
  ImageButton mRecord;
  ImageButton mSave;
  ImageButton mPlay;
  ImageButton mDelete;
  ImageButton mDoc;
  EditText mFile;
  EditText start;

  private boolean conflitAction = false;
  private boolean noRecord = false;

  private static final int RECORDER_BPP = 16;
  private static final String AUDIO_RECORDER_TEMP_FILE = "temp.raw";
  private static final String AUDIO_RECORDER_TEMP_FILE_TEMP = "temp.wav";
  private static final String AUDIO_RECORDER_TEMP_FILE_PLAYABLE = "temp.3gp";
  private static final int RECORDER_SAMPLERATE = 16000;
  private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
  private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  private static final int REQUEST_PERMISSION_CODE = 112;

  private static final String LOG_TAG = "record/play";
  private static String mFileName = null;
  private static String path = null;

  //private RecordButton mRecordButton = null;
  private AudioRecord mRecorder = null;
  private MediaRecorder mRecorderPlayable = null;
  private int bufferSize = 0;
  private Button recordButton = null;

  //private PlayButton   mPlayButton = null;
  private MediaPlayer   mPlayer = null;
  private Button playButton = null;

  private boolean permissionToRecordAccepted = false;
  private String [] permissions = {Manifest.permission.RECORD_AUDIO};

  /****************** CHECKING/RETRIEVING PERMISSIONS **********************/
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode){
      case REQUEST_PERMISSION_CODE:
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
          Toast.makeText(this, "The app received permissions ", Toast.LENGTH_LONG).show();
        else {
          Toast.makeText(this, "The app did not receive permissions ", Toast.LENGTH_LONG).show();
          finish();
        }
        break;
    }
    if (!permissionToRecordAccepted ) finish();

  }

  private boolean checkPermissions() {
    boolean pWrite = (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    if (!pWrite)
      Log.e("permission", "NO WRITE");
    boolean pRecord = (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    if (!pRecord)
      Log.e("permission", "NO RECORD");
    return pWrite && pRecord;
  }

  public void askPermissions() {
    if (!(checkPermissions())) {
      // Permission is not granted
      ActivityCompat.requestPermissions(this,new String[]{
              Manifest.permission.WRITE_EXTERNAL_STORAGE,
              Manifest.permission.RECORD_AUDIO},
          REQUEST_PERMISSION_CODE);
    }
  }

  /*********** RECORD ****************/
  private boolean recordActive = false;
  private Thread recordingThread = null;


  private void recordStart() {
    mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
    mRecorderPlayable = new MediaRecorder();
    mRecorderPlayable.setAudioSource(MediaRecorder.AudioSource.MIC);
    mRecorderPlayable.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
    mRecorderPlayable.setOutputFile(path + AUDIO_RECORDER_TEMP_FILE_PLAYABLE);
    mRecorderPlayable.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

    recordingThread = new Thread(new Runnable() {

      @Override
      public void run() {
        writeAudioDataToFile();
      }
    },"AudioRecorder Thread");

    try {
      mRecorderPlayable.prepare();
    } catch (IOException e) {
      Log.e(LOG_TAG, "prepare() failed");
    }

    mRecorderPlayable.start();
    recordingThread.start();
  }

  private void recordStop() {

    if(mRecorder != null) {
      recordActive = false;

      int i = mRecorder.getState();
      if (i == 1)
        mRecorder.stop();
      mRecorder.release();

      mRecorder = null;
      recordingThread = null;

      mRecorderPlayable.stop();
      mRecorderPlayable.release();
      mRecorderPlayable = null;
    }
    createWavFile(path + AUDIO_RECORDER_TEMP_FILE,path + AUDIO_RECORDER_TEMP_FILE_TEMP);
    //deleteTemps();
  }

  public void record() {
    if (checkPermissions()) {
      if (!recordActive) {
        start.setText("start recorder");
        recordActive = true;
        conflitAction = true;
        recordStart();
      } else {
        recordActive = false;
        conflitAction = false;
        start.setText("stop recorder");
        start.refreshDrawableState();
        recordStop();
      }
    } else
      Toast.makeText(this, "No access to mic", Toast.LENGTH_LONG).show();
  }

  private void deleteTemps() {
    File file = new File(path + AUDIO_RECORDER_TEMP_FILE);
    file.delete();
    file = new File(path + AUDIO_RECORDER_TEMP_FILE_PLAYABLE);
    file.delete();
  }

  private void writeAudioDataToFile(){
    byte data[] = new byte[bufferSize];
    String filename = path + AUDIO_RECORDER_TEMP_FILE;
    FileOutputStream os = null;

    try {
      os = new FileOutputStream(filename);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    int read = 0;

    if(null != os){
      while(recordActive){
        read = mRecorder.read(data, 0, bufferSize);

        if(AudioRecord.ERROR_INVALID_OPERATION != read){
          try {
            os.write(data);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }

      try {
        os.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private boolean createWavFile(String inFilename, String outFilename){
    FileInputStream in = null;
    FileOutputStream out = null;
    long totalAudioLen = 0;
    long totalDataLen = totalAudioLen + 36;
    long longSampleRate = RECORDER_SAMPLERATE;
    int channels = 1;
    long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

    byte[] data = new byte[bufferSize];

    //Creating wave file
    File f = new File(outFilename);
    try {
      if (!f.exists())
        f.createNewFile();
      else
        return false;
    } catch (IOException e) {
      Log.e("converter","File already exists");
    }

    try {
      in = new FileInputStream(inFilename);
      out = new FileOutputStream(outFilename);
      totalAudioLen = in.getChannel().size();
      totalDataLen = totalAudioLen + 36;

      Log.e("converter","File size: " + totalDataLen);

      WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
          longSampleRate, channels, byteRate);

      while(in.read(data) != -1){
        out.write(data);
      }

      in.close();
      out.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return true;
  }

  private void WriteWaveFileHeader(
      FileOutputStream out, long totalAudioLen,
      long totalDataLen, long longSampleRate, int channels,
      long byteRate) throws IOException {

    byte[] header = new byte[44];

    header[0] = 'R'; // RIFF/WAVE header
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    header[4] = (byte) (totalDataLen & 0xff);
    header[5] = (byte) ((totalDataLen >> 8) & 0xff);
    header[6] = (byte) ((totalDataLen >> 16) & 0xff);
    header[7] = (byte) ((totalDataLen >> 24) & 0xff);
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';
    header[12] = 'f'; // 'fmt ' chunk
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';
    header[16] = 16; // 4 bytes: size of 'fmt ' chunk
    header[17] = 0;
    header[18] = 0;
    header[19] = 0;
    header[20] = 1; // format = 1
    header[21] = 0;
    header[22] = (byte) channels;
    header[23] = 0;
    header[24] = (byte) (longSampleRate & 0xff);
    header[25] = (byte) ((longSampleRate >> 8) & 0xff);
    header[26] = (byte) ((longSampleRate >> 16) & 0xff);
    header[27] = (byte) ((longSampleRate >> 24) & 0xff);
    header[28] = (byte) (byteRate & 0xff);
    header[29] = (byte) ((byteRate >> 8) & 0xff);
    header[30] = (byte) ((byteRate >> 16) & 0xff);
    header[31] = (byte) ((byteRate >> 24) & 0xff);
    header[32] = (byte) (2 * 16 / 8); // block align
    header[33] = 0;
    header[34] = RECORDER_BPP; // bits per sample
    header[35] = 0;
    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';
    header[40] = (byte) (totalAudioLen & 0xff);
    header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
    header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
    header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

    out.write(header, 0, 44);
  }






  /****************** PLAYING RECORDS ********************/
  private boolean playerActive = false;

  private void startPlaying() {
    mPlayer = new MediaPlayer();
    try {
      mPlayer.setDataSource(path + AUDIO_RECORDER_TEMP_FILE_PLAYABLE);
      mPlayer.prepare();
      mPlayer.start();
    } catch (IOException e) {
      Log.e(LOG_TAG, "prepare() failed");
    }
  }

  private void stopPlaying() {
    mPlayer.release();
    mPlayer = null;
  }

  public void play() {
    if (!playerActive) {
      playerActive = true;
      noRecord = true;
      start.setText("play recorder");
      start.refreshDrawableState();
      startPlaying();
    } else {
      playerActive = false;
      noRecord = false;
      start.setText("pause recorder");
      start.refreshDrawableState();
      stopPlaying();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mFileName = getExternalCacheDir().getAbsolutePath();

    //demande de permission
    if (!checkPermissions())
      askPermissions();

    path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/records/";
    File storageDir = new File(path);

    if (!storageDir.exists() && !storageDir.mkdirs()) {
      Log.e("working directiry","Something very bad just happend");
      finish();
    }

    bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

    //saisie
    mFile = findViewById(R.id.fileName);
    //
    start = findViewById(R.id.start);
    //enregistrement
    mRecord = findViewById(R.id.record);
    onRecord();

    //button save
    mSave = findViewById(R.id.save);
    onSave();

    //button play
    mPlay = findViewById(R.id.play);
    onPlay();

    //button delet
    mDelete = findViewById(R.id.delete);
    onDelete();

  }

  @Override
  public void onStop() {
    super.onStop();
    if (mRecorderPlayable != null) {
      mRecorderPlayable.release();
      mRecorder = null;
    }

    if (mPlayer != null) {
      mPlayer.release();
      mPlayer = null;
    }
  }

  //debute l'enregistrement
  private void onRecord() {
    mRecord.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!noRecord)
          record();
      }
    });
  }

  //play
  private void onPlay() {
    mPlay.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if(!conflitAction)
          play();
      }
    });
  }

  //save
  public void onSave() {

    mSave.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if(!conflitAction) {
          mFileName = mFile.getText().toString();
          if (mFileName.equals("")) {
            Log.e("errors","Enter your filename");
            return;
          } else {
            File f = new File(path + mFileName);
            if (f.exists()) {
              Log.e("errors","File already exists!");
              return;
            }
          }
          InputStream is = null;
          OutputStream os = null;
          try {
            is = new FileInputStream(path + AUDIO_RECORDER_TEMP_FILE_TEMP);
            os = new FileOutputStream(path + mFileName);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
              os.write(buffer, 0, length);
            }
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            try {
              is.close();
              os.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      }
    });
  }

  //delete
  private void onDelete() {
    mDelete.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!conflitAction) {
          deleteTemps();
        }
      }
    });
  }

}
