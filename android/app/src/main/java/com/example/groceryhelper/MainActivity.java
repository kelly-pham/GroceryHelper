package com.example.groceryhelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    /**
     * Handle to the Box/Can Acctivity
     */
    private Button startbutton;

    /**
     * flag for whether we want to run in diagnostic mode or not
     */
    public static boolean DIAGNOSTIC_MODE = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);



        // Initialization
        initViewHooks(); // initialize the hooks to view screen
        initButtonListeners();

    }

    private void initViewHooks(){
        // Button Handler
        this.startbutton = (Button) findViewById(R.id.START);
    };
        /**
         * initialize on click listeners to all the buttons on screen
         */
        private void initButtonListeners() {
            // Create event handler for START button
            startbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view){
                    Intent intent = new Intent ("com.example.groceryhelper.localize.DetectorActivity");
                    startActivity(intent);
                }
            });
        };

}