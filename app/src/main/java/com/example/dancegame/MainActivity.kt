package com.example.dancegame

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.test.runner.screenshot.ScreenCapture
import androidx.test.runner.screenshot.Screenshot.capture
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.stripe.android.PaymentConfiguration
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private var bitmap: Bitmap?= null
    private var cellSelected_x=0
    private var celSelected_y=0
    private var string_share=""

    private var mHandler: Handler? = null
    private var timeInSeconds: Long=0
    private var gaming=true

    private var level=1
    private var nextLevel = false


    private var width_bonus=0
    private var options=0
    private var moves=0
    private var levelMoves=0
    private var lives=1
    private var score_lives=1
    private var scoreLevel=1
    private var bonus=0

    private var checkMovement=true

    private var movesRequire=0
    private var nameColorBlack="black_cell"
    private var nameColorWhite="white_cell"

    private var optionBlack = R.drawable.option_black
    private var optionWhite = R.drawable.option_white

    private var unloadAd=true

    private lateinit var mpBonus : MediaPlayer
    private lateinit var mpGameOver : MediaPlayer
    private lateinit var mpWin : MediaPlayer

    private lateinit var board:Array<IntArray>
    private var mInterstitialAd: InterstitialAd? = null

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private var premium: Boolean= false
    private var lastLevel = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initSound()
        initScreenGame()
        initPreferences()
    }

    override fun onResume() {
        super.onResume()
        checkPremium()
        startGame()

    }

    private fun initPreferences(){
        sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE)
        editor= sharedPreferences.edit()
    }

    private fun checkPremium(){
        premium = sharedPreferences.getBoolean("PREMIUM",false)
        if(premium){
            lastLevel = 5
            level = sharedPreferences.getInt("LEVEL",1)

            var lyPremium = findViewById<LinearLayout>(R.id.lyPremium)
            lyPremium.removeAllViews()

            var lyAdsBanner = findViewById<LinearLayout>(R.id.lyAdsBanner)
            lyAdsBanner.removeAllViews()

            var svGame = findViewById<ScrollView>(R.id.svGame)
            svGame.setPadding(0,0,0,0)

            var tvLiveData = findViewById<TextView>(R.id.tvLiveData)
            tvLiveData.background = getDrawable(R.drawable.bg_data_bottom_contrast_premium)

            var tvLiveTitle = findViewById<TextView>(R.id.tvLiveTitle)
            tvLiveTitle.background = getDrawable(R.drawable.bg_data_top_contrast_premium)

            var vNewBonus = findViewById<View>(R.id.vNewBonus)
            vNewBonus.setBackgroundColor(ContextCompat.getColor(this,
            resources.getIdentifier("contrast_data_premium","color",packageName)))

            nameColorBlack = "black_cell_premium"
            nameColorWhite = "white_cell_premium"

            optionBlack = R.drawable.option_black_premium
            optionWhite = R.drawable.option_white_premium
        }else{
            initAds()
        }
    }

    private fun initSound(){
        mpBonus = MediaPlayer.create(this,R.raw.bonus)
        mpWin = MediaPlayer.create(this,R.raw.win)
        mpGameOver = MediaPlayer.create(this,R.raw.gameover)  
    }
    private fun initAds(){
        MobileAds.initialize(this) {}

        val adView = AdView(this)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = "ca-app-pub-3940256099942544/6300978111"

        var lyAdsBanner = findViewById<LinearLayout>(R.id.lyAdsBanner)
        lyAdsBanner.addView(adView)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    fun launchPaymentCard(v: View){
        callPayment()
    }

    private fun callPayment(){
        var keyStripePayment="pk_test_wk6O7Cc5k3McBIG2Hut2irGs"
        PaymentConfiguration.init(applicationContext, keyStripePayment)

        val intent = Intent(this,CheckoutActivity::class.java)
        intent.putExtra("level",level)
        startActivity(intent)
    }

    private fun showInterstitial(){
        if (mInterstitialAd != null) {
            unloadAd=true
            mInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                override fun onAdClicked() {
                    // Called when a click is recorded for an ad.
                    //Log.d(TAG, "Ad was clicked.")
                }

                override fun onAdDismissedFullScreenContent() {
                    // Called when ad is dismissed.
                    //Log.d(TAG, "Ad dismissed fullscreen content.")
                    mInterstitialAd = null
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    // Called when ad fails to show.
                    //Log.e(TAG, "Ad failed to show fullscreen content.")
                    mInterstitialAd = null
                }

                override fun onAdImpression() {
                    // Called when an impression is recorded for an ad.
                    //Log.d(TAG, "Ad recorded an impression.")
                }

                override fun onAdShowedFullScreenContent() {
                    // Called when ad is shown.
                    //Log.d(TAG, "Ad showed fullscreen content.")
                }
            }
            mInterstitialAd?.show(this)
        }
    }

    private fun getReadyAds(){
        var adRequest = AdRequest.Builder().build()
        unloadAd=false
        InterstitialAd.load(this,"ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
            }
        })
    }
    private fun startGame(){
        if(unloadAd == true && premium == false)getReadyAds()

        setLevel()
        setLevelParameters()
        resetBoard()
        clearBoard()
        setBoardLevel()
        setFirstPosition()

        resetTime()
        startTime()
        gaming=true
    }
    private fun setBoardLevel(){
        when(level){
            2->paintLevel_2()
            3->paintLevel_3()
            4->paintLevel_4()
            5->paintLevel_5()
        }
    }
    private fun paint_Column(colum: Int){
        for(i in 0..7){
            board[colum][i]=1
            paintHorseCell(colum,i,"previus_cell")
        }
    }
    private fun paintLevel_2(){
        paint_Column(6)
    }
    private fun paintLevel_3(){
        for(i in 0..7){
            for (j in 4..7){
                board[j][i]=1
                paintHorseCell(j,i,"previus_cell")
            }
        }
    }
    private fun paintLevel_4(){
        paintLevel_3(); paintLevel_5()
    }
    private fun paintLevel_5(){
        for(i in 0..3){
            for (j in 0..3){
                board[j][i]=1
                paintHorseCell(j,i,"previus_cell")
            }
        }
    }

    private fun setLevel(){
        if(nextLevel){
            level++
            setLives()
        }else{
            if(!premium){
                lives--
                if(lives<1){
                    level = 1
                    lives=1
                }
            }
        }
    }
    private fun setLives(){
        when(level){
            1->lives=1
            2->lives=2
            3->lives=3
            4->lives=4
            5->lives=5
            6->lives=6
            7->lives=7
            8->lives=8
            9->lives=9
            10->lives=10
            11->lives=11
            12->lives=12
            13->lives=13
        }
        if(premium)lives=9999999
    }
    private fun setLevelParameters(){
        var tvLiveData= findViewById<TextView>(R.id.tvLiveData)
        tvLiveData.text=lives.toString()
        if(premium)tvLiveData.text="∞"

        score_lives = lives

        var tvLevelNumber=findViewById<TextView>(R.id.tvLevelNumber)
        tvLevelNumber.text = level.toString()
        scoreLevel = level

        bonus=0
        var tvBonusData=findViewById<TextView>(R.id.tvBonusData)
        tvBonusData.text = ""

        setLevelMoves()
        moves=levelMoves

        movesRequire=setMovesRequire()
    }
    private fun setMovesRequire():Int {
        var movesRequire=0
        when(level){
            1-> movesRequire = 5
            2-> movesRequire = 8
            3-> movesRequire = 10
            4-> movesRequire = 8
            5-> movesRequire = 8
        }
        return movesRequire
    }
    private fun setLevelMoves(){
        when(level){
            1->levelMoves=64
            2->levelMoves=56
            3->levelMoves=32
            4->levelMoves=16
            5->levelMoves=48
        }
    }
    private fun initScreenGame(){
        setSizeBoard()
        hideMessage(false)
    }
    private fun setSizeBoard(){
        var iv: ImageView

        //Obtener dimension de la pantalla y ajustar tablero
        val display=windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x

        var width_dp = (width / resources.displayMetrics.density)

        var lateralMarginsDP=0
        val width_cell = (width_dp - lateralMarginsDP)/8
        val height_cell= width_cell

        width_bonus=2*width_cell.toInt()

        for(i in 0..7) {
            for (j in 0..7) {
                iv = findViewById(resources.getIdentifier("c$i$j", "id", packageName))
                var heigth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,height_cell, resources.displayMetrics).toInt()
                var width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,width_cell, resources.displayMetrics).toInt()

                iv.setLayoutParams(TableRow.LayoutParams(width,heigth))
            }
        }
    }
    private fun hideMessage(start: Boolean){
        var lyMessage= findViewById<LinearLayout>(R.id.lyMessage)
        lyMessage.visibility = View.INVISIBLE

        if(start) startGame()
    }

    fun checkCellClicked(v: View){
        var name=v.tag.toString()

        var x=name.subSequence(1,2).toString().toInt()
        var y=name.subSequence(2,3).toString().toInt()

        checkCell(x,y)
    }
    private fun checkCell(x: Int,y: Int){
        var checkTrue=true
        if(checkMovement){
            var dif_x = x-cellSelected_x
            var dif_y = y-celSelected_y

            checkTrue=false
            if(dif_x==1 && dif_y==2) checkTrue=true
            if(dif_x==1 && dif_y==-2) checkTrue=true
            if(dif_x==2 && dif_y==1) checkTrue=true
            if(dif_x==2 && dif_y==-1) checkTrue=true
            if(dif_x==-1 && dif_y==2) checkTrue=true
            if(dif_x==-1 && dif_y==-2) checkTrue=true
            if(dif_x==-2 && dif_y==1) checkTrue=true
            if(dif_x==-2 && dif_y==-1) checkTrue=true
            if(board[x][y] == 1) checkTrue=false

            if(checkTrue==true) selectCell(x,y)
        }else{
            if(board[x][y] != 1){
                bonus--
                var tvBonusData=findViewById<TextView>(R.id.tvBonusData)
                tvBonusData.text=" +$bonus"
                if(bonus==0) tvBonusData.text=""
            }
        }
        if(board[x][y] == 1) checkTrue = false
        if(checkTrue) selectCell(x,y)
    }
    private fun selectCell(x: Int,y: Int){
        moves--
        var tvMovesData= findViewById<TextView>(R.id.tvMovesData)
        tvMovesData.text=moves.toString()

        growProgressBonus()

        if(board[x][y] == 2){
            bonus++
            var tvBonusData = findViewById<TextView>(R.id.tvBonusData)
            tvBonusData.text=" + $bonus"
        }

        board[x][y]=1
        paintHorseCell(cellSelected_x,celSelected_y,"previus_cell")

        cellSelected_x=x
        celSelected_y=y

        clearOptions()

        paintHorseCell(x,y,"selected_cell")
        checkMovement=true
        checkOptions(x,y)

        if(moves>0){
            checkNewBonus()
            checkGameOver()
        }else{
            showMessage("You Win!!","Next Level!",false)
        }
    }

    private fun resetBoard(){
        // 0 esta libre
        // 1 casilla marcada
        // 2 es un bonus
        // 9 es una opcion de movimiento actual
        board= arrayOf(
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0)
        )
    }
    private fun clearBoard(){
        var iv: ImageView
        var colorBlack= ContextCompat.getColor(this,
        resources.getIdentifier(nameColorBlack,"color",packageName))
        var colorWhite= ContextCompat.getColor(this,
            resources.getIdentifier(nameColorWhite,"color",packageName))

        for(i in 0..7){
            for(j in 0..7){
                iv=findViewById(resources.getIdentifier("c$i$j","id",packageName))
                iv.setImageResource(0)

                if(checkColorCell(i,j)=="black")iv.setBackgroundColor(colorBlack)
                else iv.setBackgroundColor(colorWhite)
            }
        }
    }
    // Genera de forma aleatoria la primera posicion
    private fun setFirstPosition(){
        var x=0
        var y=0

        var firstPosition=false
        while(firstPosition==false){
            x=(0..7).shuffled().first()
            y=(0..7).shuffled().first()
            if(board[x][y]==0) firstPosition=true
            checkOptions(x,y)
            if(options == 0) firstPosition = false
        }
        cellSelected_x=x
        celSelected_y=y

        selectCell(x,y)
    }

    private fun growProgressBonus(){
        var moves_done= levelMoves - moves
        var bonus_done = moves_done / movesRequire
        var moves_rest= movesRequire * (bonus_done)
        var bonus_grow = moves_done - moves_rest

        var v = findViewById<View>(R.id.vNewBonus)
        var widthBonus = ((width_bonus/movesRequire)*bonus_grow).toFloat()

        var height= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,8f,resources.displayMetrics).toInt()
        var widht= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,widthBonus,resources.displayMetrics).toInt()
        v.setLayoutParams(TableRow.LayoutParams(widht,height))
    }

    private fun checkNewBonus(){
        if(moves% movesRequire==0){
            var bonusCell_x=0
            var bonusCell_y=0

            var bonusCell=false
            while (bonusCell==false){
                bonusCell_x=(0..7).shuffled().first()
                bonusCell_y=(0..7).shuffled().first()
                if(board[bonusCell_x][bonusCell_y]==0){
                    bonusCell=true
                }
            }
            board[bonusCell_x][bonusCell_y]=2
            paintBonusCell(bonusCell_x,bonusCell_y)
        }
    }
    private fun checkMove(x: Int,y: Int,mov_x: Int,mov_y: Int){
        var option_x = x + mov_x
        var option_y= y + mov_y

        if(option_x < 8 && option_y < 8 && option_y >= 0 && option_x >=0){
            if(board[option_x][option_y] == 0 || board[option_x][option_y] == 2){
                options++
                paintOptions(option_x,option_y)

                if(board[option_x][option_y]==0)board[option_x][option_y]=9
            }
        }
    }
    private fun checkColorCell(x: Int, y: Int): String{
        var color=""
        var blackColum_x= arrayOf(0,2,4,6)
        var blackRow_x= arrayOf(1,3,5,7)
        if((blackColum_x.contains(x) && blackColum_x.contains(y))
            || (blackRow_x.contains(x) && blackRow_x.contains(y))){
            color="black"
        }else{
            color="white"
        }
        return color
    }
    private fun checkGameOver(){
        if(options==0){
            if(bonus>0){
                checkMovement=false
                paintAllOptions()
            }else{
                showMessage("Game Over","Try Again!",true)
            }
        }
    }

    private fun paintBonusCell(x: Int,y: Int){
        var iv: ImageView= findViewById(resources.getIdentifier("c$x$y", "id", packageName))
        iv.setImageResource(R.drawable.bonus)
    }
    private fun paintAllOptions(){
        for(i in 0..7){
            for(j in 0..7){
                if(board[i][j] != 1){
                    paintOptions(i,j)
                }
                if(board[i][j]==0){
                    board[i][j]=9
                }
            }
        }
    }
    private fun paintOptions(x: Int, y: Int){
        var iv: ImageView= findViewById(resources.getIdentifier("c$x$y", "id", packageName))
        if(checkColorCell(x,y)=="black")iv.setBackgroundResource(optionBlack)
        else iv.setBackgroundResource(optionWhite)
    }
    private fun paintHorseCell(x: Int,y: Int,color: String){
        var iv: ImageView= findViewById(resources.getIdentifier("c$x$y", "id", packageName))
        iv.setBackgroundColor(ContextCompat.getColor(this,resources.getIdentifier(color,"color",packageName)))
        iv.setImageResource(R.drawable.icon)
    }

    private fun showMessage(title: String, action: String, gameOver: Boolean){
        movesRequire
        nextLevel = !gameOver

        var lyMessage= findViewById<LinearLayout>(R.id.lyMessage)
        lyMessage.visibility = View.VISIBLE

        var tvTitlesMessage= findViewById<TextView>(R.id.tvTitleMessage)
        tvTitlesMessage.text = title

        var tvTimeData = findViewById<TextView>(R.id.tvTimeData)
        var score: String=""
        if(gameOver){
            if(premium==false) showInterstitial()
            score = "Score" + (levelMoves-moves) + "/"+ levelMoves
            string_share="This game makes me sick !!! "+ score + "retoCaballo"
        }else{
            score=tvTimeData.text.toString()
            string_share="let´s go!!! New challenge completed. Level: $level ("+ score + "retoCaballo"
        }

        var tvScoreMessage= findViewById<TextView>(R.id.tvScoreMessage)
        tvScoreMessage.text=score

        var tvAction= findViewById<TextView>(R.id.tvActionn)
        tvAction.text=action
    }

    private fun clearOptions(){
        for(i in 0..7){
            for(j in 0..7){
                if(board[i][j]==9 || board[i][j] == 2){
                    if(board[i][j] == 9 ) board[i][j]=0
                    clearOption(i,j)
                }
            }
        }
    }
    private fun clearOption(x: Int, y: Int){
        var iv: ImageView= findViewById(resources.getIdentifier("c$x$y", "id", packageName))
        if(checkColorCell(x,y)=="black")
            iv.setBackgroundColor(ContextCompat.getColor(this,resources.getIdentifier(nameColorBlack,"color",packageName)))
        else
            iv.setBackgroundColor(ContextCompat.getColor(this,resources.getIdentifier(nameColorWhite,"color",packageName)))
        if(board[x][y] == 1) iv.setBackgroundColor(ContextCompat.getColor(this,resources.getIdentifier("previus_cell","color",packageName)))
    }
    private fun checkOptions(x: Int,y: Int){
        options=0

        checkMove(x,y,1,2)
        checkMove(x,y,2,1)
        checkMove(x,y,1,-2)
        checkMove(x,y,2,-1)
        checkMove(x,y,-1,2)
        checkMove(x,y,-2,1)
        checkMove(x,y,-1,-2)
        checkMove(x,y,-2,-1)

        var tvOptionsData= findViewById<TextView>(R.id.tvOptionsData)
        tvOptionsData.text=options.toString()
    }

    private fun resetTime(){
        mHandler?.removeCallbacks(chronometer)
        timeInSeconds=0

        var tvTimeData= findViewById<TextView>(R.id.tvTimeData)
        tvTimeData.text="00:00"
    }
    private fun startTime(){
        mHandler = Handler(Looper.getMainLooper())
        chronometer.run()
    }
    private var chronometer: Runnable= object: Runnable{
        override fun run() {
            try{
                if(gaming==true){
                    timeInSeconds++
                    updateStopWatchView(timeInSeconds)
                }
            }finally {
                mHandler!!.postDelayed(this,1000L)
            }
        }
    }
    private fun updateStopWatchView(timeInSeconds: Long){
        val formattedTime=getFormattedStopWatch((timeInSeconds*1000))
        var tvTimeData=findViewById<TextView>(R.id.tvTimeData)
        tvTimeData.text=formattedTime
    }
    private fun getFormattedStopWatch(ms: Long): String{
        var milliseconds = ms
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds-= TimeUnit.MINUTES.toMillis(minutes)
        val seconds=TimeUnit.MILLISECONDS.toSeconds(milliseconds)

        return "${if (minutes<10) "0" else ""}$minutes:"+
                "${if (seconds < 10) "0" else ""}$seconds"
    }

    fun launchAction(v: View){
        hideMessage(true)
    }

    fun launchShareGame(v: View){
        shareGame()
    }
    private fun shareGame(){
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),1)

        var ssc: ScreenCapture = capture(this)
        bitmap =ssc.getBitmap()

        if(bitmap != null){
            var idGame = SimpleDateFormat("yyyy/MM/dd").format(Date())
            idGame= idGame.replace(":","")
            idGame = idGame.replace("/","")

            val path = saveImage(bitmap,"${idGame}.jpg")
            val bmUri = Uri.parse(path)

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            shareIntent.putExtra(Intent.EXTRA_STREAM,bmUri)
            shareIntent.putExtra(Intent.EXTRA_TEXT,string_share)
            shareIntent.type="image/png"

            val finalShareIntent = Intent.createChooser(shareIntent,"Select tha app you want to share the game to")
            finalShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.startActivity(finalShareIntent)
        }
    }
    private fun saveImage(bitmap: Bitmap?,fileName: String): String?{
        if(bitmap==null)
            return null

        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q){
            val contentValues= ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,fileName)
                put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_PICTURES + "/Screenshots")
            }
            val uri=this.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,contentValues)
            if(uri != null){
                this.contentResolver.openOutputStream(uri).use {
                    if(it == null)
                        return@use

                    bitmap.compress(Bitmap.CompressFormat.PNG,85, it)
                    it.flush()
                    it.close()

                    MediaScannerConnection.scanFile(this, arrayOf(uri.toString()),null,null)
                }
            }
            return uri.toString()
        }
        val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES+"/Screenshots").absolutePath

        val dir = File(filePath)
        if(!dir.exists()) dir.mkdirs()
        val file= File(dir,fileName)
        val fOut = FileOutputStream(file)

        bitmap.compress(Bitmap.CompressFormat.PNG,85,fOut)
        fOut.flush()
        fOut.close()

        MediaScannerConnection.scanFile(this, arrayOf(file.toString()),null,null)
        return filePath
    }
}
