package com.mmadi_anzilane.speechrecorder;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity {

  /*********************************************************************************
  ****************** BACKEND FUNCTIONS : WRITE, CONVERT & SAVE ********************
  ****************** FAIT PAR           Kruglov Nikita          *******************
  *********************************************************************************/

  private boolean conflitAction = false;
  private boolean noRecord = false;

  private static final int RECORDER_BPP = 16;
  private static final String AUDIO_RECORDER__RAW_TEMP_FILE = "temp.raw"; //raw file
  private static final String AUDIO_RECORDER_WAV_TEMP_FILE = "temp.wav"; //result wav file
  private static final String AUDIO_RECORDER_3GP_TEMP_FILE = "temp.3gp"; //playable sample
  private static final String AUDIO_RECORDER_EXTENTION = ".wav"; //playable sample
  //16kHz, mono, 16 bit
  private static final int RECORDER_SAMPLERATE = 16000;
  private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
  private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  private static final int REQUEST_PERMISSION_CODE = 112;

  private static String mFileName = null;
  private static String path = null;

  //Recorder
  private AudioRecord mRecorder = null;
  private MediaRecorder mRecorderPlayable = null;
  private int bufferSize = 0;

  //Player
  private MediaPlayer   mPlayer = null;


  /****************** CHECKING/RETRIEVING PERMISSIONS **********************/
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode){
      case REQUEST_PERMISSION_CODE:
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
          Toast.makeText(this, "The app received permissions ", Toast.LENGTH_LONG).show();
        else {
          //No permissions
          //TODO Bug : permission should be requested multiple times, not once
          Toast.makeText(this, "The app did not receive permissions ", Toast.LENGTH_LONG).show();
          finish();
        }
        break;
    }
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
      // Permission is not granted - request
      ActivityCompat.requestPermissions(this,new String[]{
              Manifest.permission.WRITE_EXTERNAL_STORAGE,
              Manifest.permission.RECORD_AUDIO},
          REQUEST_PERMISSION_CODE);
    }
  }

  /************** RECORD ****************/
  private boolean recordActive = false;
  private Thread recordingThread = null;


  private boolean recordStart() {
    deleteTemps(); //Cleanup
    bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);
    mRecorderPlayable = new MediaRecorder();
    mRecorderPlayable.setAudioSource(MediaRecorder.AudioSource.MIC);
    mRecorderPlayable.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
    mRecorderPlayable.setOutputFile(path + AUDIO_RECORDER_3GP_TEMP_FILE);
    mRecorderPlayable.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
    //2 records in parallel : to raw (in bytes) and to 3gp (to play)
    recordingThread = new Thread(new Runnable() {
      @Override
      public void run() { writeAudioDataToFile(); }
    },"Recorder Thread");

    try {
      mRecorderPlayable.prepare();
    } catch (IOException e) {
      Log.e("recorder", "prepare() failed");
    }

    mRecorderPlayable.start();
    recordingThread.start();

    return true;
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

    createWavFile(path + AUDIO_RECORDER__RAW_TEMP_FILE,path + AUDIO_RECORDER_WAV_TEMP_FILE);
    //deleteTemps();
  }

  public void onRecord(View v) {
    if (checkPermissions()) {
      if (canUseRecordButton) {
        if (!recordActive) {
          recordActive = true;
          buttonRecord.setImageResource(R.drawable.mic_on_big);
          deactivatePreviewButtons();
          Toast.makeText(this, "Recording...", Toast.LENGTH_LONG).show();
          recordActive = recordStart();
        } else {
          recordActive = false;
          buttonRecord.setImageResource(R.drawable.mic_off_big);
          activatePreviewButtons();
          recordStop();
          canUseRecordButton = true;
          Toast.makeText(this, "Record finished", Toast.LENGTH_LONG).show();
        }
      }
    } else
      Toast.makeText(this, "No access to mic", Toast.LENGTH_LONG).show();
  }

  /* Functions necessary to store and convert files*/
  private void deleteTemps() {
    File file = new File(path + AUDIO_RECORDER__RAW_TEMP_FILE);
    if (file.exists())
      file.delete();
    file = new File(path + AUDIO_RECORDER_3GP_TEMP_FILE);
    if (file.exists())
      file.delete();
  }

  private void writeAudioDataToFile(){
    byte data[] = new byte[bufferSize];
    String filename = path + AUDIO_RECORDER__RAW_TEMP_FILE;
    FileOutputStream os = null;
    int read = 0;

    try {
      os = new FileOutputStream(filename);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    if(os != null){
      while(recordActive){
        //Read a part of data
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

      writeHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);

      while(in.read(data) != -1)
        out.write(data);

      in.close();
      out.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return true;
  }

  private void writeHeader(
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

  private boolean startPlaying() {
    mPlayer = new MediaPlayer();
    try {
      mPlayer.setDataSource(path + AUDIO_RECORDER_3GP_TEMP_FILE);
      setEndORecordListener();
      mPlayer.prepare();
      mPlayer.start();
    } catch (IOException e) {
      Toast.makeText(this, "No record to Play", Toast.LENGTH_LONG).show();
      return false;
    }
    return true;
  }

  //Stop playing at the end of the record
  void setEndORecordListener() {
    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        playerActive = false;
        buttonPlay.setImageResource(R.drawable.play);
        stopPlaying();
        activateRecordButtons();
      }
    });
  }

  private void stopPlaying() {
    mPlayer.release();
    mPlayer = null;
  }

  public void onPlay(View v) {
    if (canUsePreviewButton) { //if buffer contain record
      if (!playerActive) {
        playerActive = true;
        buttonPlay.setImageResource(R.drawable.stop);
        deactivateRecordButtons();
        playerActive = startPlaying();
      } else {
        playerActive = false;
        buttonPlay.setImageResource(R.drawable.play);
        stopPlaying();
        activateRecordButtons();
      }
    }
  }

  /*********************************************************************************
   ****************** FRONTEND FUNCTIONS : FILES, WINDOWS, LISTENERS ***************
   ****************** FAIT PAR           Mmadi Anzilane          *******************
   *********************************************************************************/

  //mes composants graphique
  ImageView buttonRecord;
  ImageView buttonSave;
  ImageView buttonPlay;
  ImageView buttonDelete;
  Button buttonShowRecords;


  /* Button switchers */
  boolean canUseRecordButton = true; //flag "can u use record button"
  boolean canUsePreviewButton = false; //flag "can u use play and save buttons"

  private void deactivateRecordButtons() {
    canUseRecordButton = false;
    buttonRecord.setImageResource(R.drawable.mic_unaccessible);
  }

  private void activateRecordButtons() {
    canUseRecordButton = true;
    buttonRecord.setImageResource(R.drawable.mic_off_big);
  }

  private void deactivatePreviewButtons() {
    buttonPlay.setImageResource(R.drawable.play_unaccessible);
    buttonSave.setImageResource(R.drawable.save_unaccessible);
    canUsePreviewButton = false;
  }

  private void activatePreviewButtons() {
    buttonPlay.setImageResource(R.drawable.play);
    buttonSave.setImageResource(R.drawable.save);
    canUsePreviewButton = true;
  }


  /****************** LISTENERS ********************/
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

    //enregistrement
    buttonRecord = findViewById(R.id.record);

    //button save
    buttonSave = findViewById(R.id.save);

    //button play
    buttonPlay = findViewById(R.id.play);

    //button delet
    //buttonDelete = findViewById(R.id.delete);

    //Shot recorded files
    buttonShowRecords = findViewById(R.id.showRecords);

    setListeners();

  }
  /****************** LISTENERS ********************/
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
    deleteTemps();
  }

  //debute l'enregistrement
  private void setListeners() {

    buttonRecord.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { onRecord(v); }
    });

    buttonPlay.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { onPlay(v); }
    });

    buttonSave.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (canUsePreviewButton) { //if buffer contain record
          savingDialog();
        }
      }
    });


    buttonShowRecords.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        showFilesDialog();
      }
    });
  }

  private String userInout = "";
  boolean decidedToSave = false;

  /* SAVING DIALOG */
  public void savingDialog() {

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Enter file name");

    // Set up the input
    final EditText input = new EditText(this);
    // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    builder.setView(input);

    // Set up the buttons
    builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        userInout = input.getText().toString();
        mFileName = userInout;
        saveFinalResult();
      }
    });
    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
      }
    });

    builder.show();
  }

  private void saveFinalResult() {
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
      is = new FileInputStream(path + AUDIO_RECORDER_WAV_TEMP_FILE);
      os = new FileOutputStream(path + mFileName + AUDIO_RECORDER_EXTENTION);
      byte[] buffer = new byte[1024];
      int length;
      while ((length = is.read(buffer)) > 0) {
        os.write(buffer, 0, length);
      }
      Toast.makeText(this, mFileName + AUDIO_RECORDER_EXTENTION + " saved!", Toast.LENGTH_LONG).show();
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

  //Dialog to show fils in working directory
  public void showFilesDialog() {

    File folder = new File(path);
    String fs = "";
    for (File f : folder.listFiles()) {
      if (f.isFile() && f.getName().matches(".*\\.wav") && !f.getName().equals("temp.wav")) {
        fs += f.getName() + "\n";
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Existing records");

    builder.setMessage(fs);

    // Set up the buttons
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
      }
    });

    builder.show();
  }

}

