// 21-6-19 :
// - Add Publish Data Feature
// 09-6-19 :
// - Add Get Robot Heading Feature
// - Remove graph
// 30-5-19 :
// - Change graph to position
// - Remove Gyro Yaw
// - Add Gyro Yaw
// 30-4-19 :
// - Publish Position in Thread
// 20-4-19 :
// - Publish Position
// 06-4-19 :
// - Fix Position Displacement
// 03-4-19 :
// - Fix Heading
// 02-4-19 :
// - Cleaning Variables
// 30-3-19 :
// - Smoothing All Data Calculation
// 28-3-19 :
// - Correcting Heading using Auto Calibration
// 27-3-19 :
// - Fusion Accel and Magneto Data to get Heading
// 23-3-19 :
// - Change isStart Threshold to 2 deg
// - Remove Coroutine (cause of broken data)
// - Remove Length from Features
// 22-3-19 :
// - Add Stride Length from Forward Pass ANN
// - Add All Weight and Bias from CSV File
// 21-3-19 :
// - Add Initial Heading to CSV File
// - Remove All Calibration Script

package com.m.streadesc

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.opencsv.CSVReader
import io.moquette.BrokerConstants
import io.moquette.server.Server
import io.moquette.server.config.MemoryConfig
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.Math.abs
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private var etStrideLength: TextView? =null
    private var etData1: TextView? = null
    private var etData2: TextView? = null
    private var etData3: TextView? = null
    private var tvData1: TextView? = null
    private var tvData2: TextView? = null
    private var tvData3: TextView? = null
    private var tvHeading: TextView? = null
    var isLogged:Boolean = false
//    private var mseries: LineGraphSeries<DataPoint>? = null
    private var dataNum:Int = 0
//    private var graph: GraphView? = null
    var rawWriter: FileWriter? = null
    var processedWriter: FileWriter? = null
    private var mean: List<Array<String>> = listOf()
    private var std: List<Array<String>> = listOf()
    private var wih1: List<Array<String>> = listOf()
    private var bih1: List<Array<String>> = listOf()
    private var wo: List<Array<String>> = listOf()
    private var bo: List<Array<String>> = listOf()
    private var btLog: Button? = null
    private var btPublish: Button? = null
    private var btGetInfo: Button? = null
//    private var btReset: Button? = null
    private var client: MqttAndroidClient? = null
    private var btAdd:Button? = null
    private var targetForm:LinearLayout? = null
    private var etTarget:EditText? = null
    var accXSeparatedLine:Array<String> = arrayOf()
    var accYSeparatedLine:Array<String> = arrayOf()
    var accZSeparatedLine:Array<String> = arrayOf()
    var gyrXSeparatedLine:Array<String> = arrayOf()
    var gyrYSeparatedLine:Array<String> = arrayOf()
    var gyrZSeparatedLine:Array<String> = arrayOf()
    var magXSeparatedLine:Array<String> = arrayOf()
    var magYSeparatedLine:Array<String> = arrayOf()
    var magZSeparatedLine:Array<String> = arrayOf()
    var temSeparatedLine:Array<String> = arrayOf()
    var process = 0
    var accM1 = ArrayList<Double>()
    var gyrM1 = ArrayList<Double>()
    var temM1 = 0.0
    private var buffAccGyr = ArrayList<ArrayList<Double>>()
    private var buffTemp = 0.0
    private var buffStrideFrequency = 0
    private var strideCount = 0
    private var accGyrMax = ArrayList<Double>()
    private var accGyrMin = ArrayList<Double>()
    private var accGyrMean = ArrayList<Double>()
    private var accGyrVar = ArrayList<Double>()
    private var strideFrequency = 0
    private var readyData = ""
    private var addCondition = 0
    var buffEndHeading = 0.0
    var isOnlineMode = true
    private var isFollowerActive = true
    var maxMag = arrayOf(110.0,35.0,-10.0)
    var minMag = arrayOf(20.0,-50.0,-94.0)
    var skip = true
//    private var strideLength = 0.0
    var orientation = arrayListOf(0.0,0.0,0.0)
    var iRobotWaypoint = 0
    private var iRobotWaypointCheck = 0
    private var iHumanWaypoint = 0
    private var arrStrideLength = ArrayList<Double>()
    private var arrHeading = ArrayList<Double>()
//    var yawGyr = 0.0
    var gBias = arrayListOf(0.0,0.0,0.0)
    var init = true
//    private var x = 0.0
//    private var y = 0.0
    val roll = 1.366593
    val pitch = -0.0108210414
    private var etTopic: EditText? = null
    private var etMessage: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(requestPermission()){
            initVariable()
            initSpinner()
            startMqttServer()
            buttonListener()
            getNNModel()
        }
    }

    private fun requestPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),3177)
            false
        } else{
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            3177 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initVariable()
                    initSpinner()
                    startMqttServer()
                    buttonListener()
                }
                else { }
                return
            }
            else -> {
            }
        }
    }

    private fun initVariable() {
        etTopic = findViewById(R.id.etTopic)
        etTopic?.text = Editable.Factory.getInstance().newEditable("PDR/Leader/Position")
        etMessage = findViewById(R.id.etMessage)
        etData1 = findViewById(R.id.etData1)
        etData2 = findViewById(R.id.etData2)
        etData3 = findViewById(R.id.etData3)
        tvData1 = findViewById(R.id.tvData1)
        tvData2 = findViewById(R.id.tvData2)
        tvData3 = findViewById(R.id.tvData3)
        tvHeading = findViewById(R.id.tvHeading)
        etStrideLength = findViewById(R.id.stride_length)
        etTarget = findViewById(R.id.etTarget)
        btLog = findViewById(R.id.btLog)
        btAdd = findViewById(R.id.btAdd)
        btGetInfo = findViewById(R.id.btGetInfo)
//        btReset = findViewById(R.id.btReset)
        btPublish = findViewById(R.id.btPublish)
        targetForm = findViewById(R.id.targetForm)
        for(i in 0..2){
            accM1.add(0.0)
            gyrM1.add(0.0)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        when (parent.id) {
            R.id.sensorData -> {
                dataNum = pos
                when(dataNum){
                    0->{
                        tvData1?.text = "Acc-X"
                        tvData2?.text = "Acc-Y"
                        tvData3?.text = "Acc-Z"
                    }
                    1->{
                        tvData1?.text = "Gyr-X"
                        tvData2?.text = "Gyr-Y"
                        tvData3?.text = "Gyr-Z"
                    }
                    2->{
                        tvData1?.text = "Mag-X"
                        tvData2?.text = "Mag-Y"
                        tvData3?.text = "Mag-Z"
                    }
                    3->{
                        tvData1?.text = "Roll"
                        tvData2?.text = "Pitch"
                        tvData3?.text = "Yaw"
                    }
                }
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
    }

//    private fun initGraph(){
//        mseries = LineGraphSeries()
//        graph = findViewById(R.id.graph)
//        graph?.addSeries(mseries)
//        graph?.viewport?.isXAxisBoundsManual = true
//        graph?.viewport?.setMinX(-500.0)
//        graph?.viewport?.setMaxX(500.0)
//    }
//
//    private fun resetGraph(){
//        graph?.removeAllSeries()
//    }

    private fun initSpinner(){
        val sensorData: Spinner = findViewById(R.id.sensorData)
        sensorData.onItemSelectedListener = this
        ArrayAdapter.createFromResource(
                this,
                R.array.sensor_data,
                android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sensorData.adapter = adapter
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buttonListener(){
        btLog?.setOnClickListener {
            val topic = "PDR/isStart"
            val payload: String = if (isLogged) {
                "0"
            } else {
                "1"
            }

            btLog?.text = "Please Wait..."
            publishMQTT(isLogged, topic, payload)
        }
        btGetInfo?.setOnClickListener{
            val token = client!!.connect()
            token!!.actionCallback = object : IMqttActionListener {
                @SuppressLint("SetTextI18n")
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    try {
                        subscribeTopic()
                        client!!.publish("PDR/Follower/GetHeading",  MqttMessage("1".toByteArray()))
                        btGetInfo!!.text = "Please Wait"
                    } catch (e: MqttException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                @SuppressLint("SetTextI18n")
                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                }
            }
        }
        btAdd?.setOnClickListener{
            when(addCondition){
                0->{
                    readyData+= "," + etTarget?.text
                    try {
                        processedWriter?.append(readyData)?.append("\n")
                        processedWriter?.flush()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    btAdd!!.text = "Start Again"
                    addCondition=1
                }
                1->{
                    client!!.publish("PDR/isStart",  MqttMessage("1".toByteArray()))    // Start Measuring Data
                    targetForm?.visibility = View.GONE
                    btAdd!!.text = "Add"
                    addCondition=0
                }
            }
        }
        btPublish?.setOnClickListener {
            val token = client!!.connect()
            token!!.actionCallback = object : IMqttActionListener {
                @SuppressLint("SetTextI18n")
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    try {
                        client!!.publish(etTopic?.text.toString(),  MqttMessage(etMessage?.text.toString().toByteArray()))
                    } catch (e: MqttException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                @SuppressLint("SetTextI18n")
                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                }
            }
        }
    }

    private fun startMqttServer() {
        val server = Server()
        try
        {
            val memoryConfig = MemoryConfig(Properties())
            memoryConfig.setProperty(
                    BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME,
                    Environment.getExternalStorageDirectory().absolutePath + File.separator + BrokerConstants.DEFAULT_MOQUETTE_STORE_MAP_DB_FILENAME
            )
            server.startServer(memoryConfig)
            Toast.makeText(applicationContext, "MQTT Server has Started", Toast.LENGTH_LONG).show()
            val clientId = MqttClient.generateClientId()
            if(client==null){
                client = MqttAndroidClient(applicationContext, "tcp://192.168.43.1:1883", clientId)
            }
        }
        catch (e: IOException) {
            e.printStackTrace()
        }
        catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun publishMQTT(status: Boolean?, topic: String, message: String) {
        val token = client!!.connect()
        token!!.actionCallback = object : IMqttActionListener {
            @SuppressLint("SetTextI18n")
            override fun onSuccess(asyncActionToken: IMqttToken) {
                try {
                    subscribeTopic()
                    if (status!!) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        btLog?.text = "START"
                        iHumanWaypoint = 0
                        iRobotWaypoint = 0
                        client!!.unsubscribe(topic)
//                        resetGraph()
                        rawWriter?.close()
                        if (!isOnlineMode){
                            processedWriter?.close()
                        }
                        isLogged = false
                    } else {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//                        initGraph()
                        init = true
//                        x = 0.0
//                        y = 0.0
//                        yawGyr = 0.0
                        btLog?.text = "STOP"
                        val time = System.currentTimeMillis() / 1000
                        val rawFile =
                                File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Data/" + time + "-raw.csv")
                        if (!rawFile.exists()) {
                            rawFile.createNewFile()
                        }
                        val processedFile =
                                File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Data/" + time + "-processed.csv")
                        if (!processedFile.exists()) {
                            processedFile.createNewFile()
                        }
                        processedWriter = FileWriter(processedFile)
                        rawWriter = FileWriter(rawFile)
//                        subscribeTopic()
                        isLogged = true
                    }
                    val encodedPayload = message.toByteArray()
                    val mqttmessage = MqttMessage(encodedPayload)
                    client!!.publish(topic, mqttmessage)
                } catch (e: MqttException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            @SuppressLint("SetTextI18n")
            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                if (status!!) {
                    btLog?.text = "STOP"
                    isLogged = true
                } else {
                    btLog?.text = "START"
                    isLogged = false
                }
                Toast.makeText(applicationContext, "Failed to Connect to Server, Please Restart The Application", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onCheckboxClicked(view: View) {
        if (view is CheckBox) {
            val checked: Boolean = view.isChecked
            when (view.id) {
                R.id.cbOnlineMode -> {
                    isOnlineMode = checked
                }
                R.id.cbFollower -> {
                    isFollowerActive = checked
                }
            }
        }
    }

    private fun getNNModel() = try {
        val meanFile = File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Model/mean.csv")
        val stdFile = File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Model/std.csv")
        val wih1File = File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Model/wih1.csv")
        val woFile = File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Model/wo.csv")
        val bih1File = File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Model/bih1.csv")
        val boFile = File(Environment.getExternalStorageDirectory().toString() + "/Documents/TA/Model/bo.csv")

        mean = CSVReader(FileReader(meanFile.absolutePath)).readAll()
        std = CSVReader(FileReader(stdFile.absolutePath)).readAll()
        wih1 = CSVReader(FileReader(wih1File.absolutePath)).readAll()
        bih1 = CSVReader(FileReader(bih1File.absolutePath)).readAll()
        wo = CSVReader(FileReader(woFile.absolutePath)).readAll()
        bo = CSVReader(FileReader(boFile.absolutePath)).readAll()
    }catch (e: IOException) {
        e.printStackTrace()
    }

    private fun subscribeTopic() {
        val tokenSub = client?.subscribe("PDR/#", 0)
        tokenSub?.actionCallback = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                client?.setCallback(object : MqttCallbackExtended {
                    override fun connectionLost(cause: Throwable?) {}
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {}
                    @SuppressLint("SetTextI18n")
                    override fun messageArrived(topic: String, mqttMessage: MqttMessage) {
                        if (skip) {
                            skip=false
                        } else {
                            when (topic) {
                                "PDR/isReceived" -> {
                                    iRobotWaypoint++
                                }
                                "PDR/Follower/Heading" -> {
                                    val robotHeading = String(mqttMessage.payload)
                                    Toast.makeText(applicationContext, "Robot Heading : $robotHeading Deg", Toast.LENGTH_SHORT).show()
                                    btGetInfo!!.text = "Get Info"
                                }
                                "PDR/Data/Acc/X" -> {
                                    val accXData = String(mqttMessage.payload)
                                    accXSeparatedLine = accXData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Acc/Y" -> {
                                    val accYData = String(mqttMessage.payload)
                                    accYSeparatedLine = accYData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Acc/Z" -> {
                                    val accZData = String(mqttMessage.payload)
                                    accZSeparatedLine = accZData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Gyr/X" -> {
                                    val gyrXData = String(mqttMessage.payload)
                                    gyrXSeparatedLine = gyrXData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Gyr/Y" -> {
                                    val gyrYData = String(mqttMessage.payload)
                                    gyrYSeparatedLine = gyrYData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Gyr/Z" -> {
                                    val gyrZData = String(mqttMessage.payload)
                                    gyrZSeparatedLine = gyrZData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Mag/X" -> {
                                    val magXData = String(mqttMessage.payload)
                                    magXSeparatedLine = magXData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Mag/Y" -> {
                                    val magYData = String(mqttMessage.payload)
                                    magYSeparatedLine = magYData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Mag/Z" -> {
                                    val magZData = String(mqttMessage.payload)
                                    magZSeparatedLine = magZData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                }
                                "PDR/Data/Tem" -> {
                                    val temData = String(mqttMessage.payload)
                                    temSeparatedLine = temData.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                    for(i in 0 until temSeparatedLine.size){
                                        val allData = accXSeparatedLine[i] + "," + accYSeparatedLine[i] + "," + accZSeparatedLine[i] + "," + gyrXSeparatedLine[i] + "," + gyrYSeparatedLine[i] + "," + gyrZSeparatedLine[i] + "," + magXSeparatedLine[i] + "," + magYSeparatedLine[i] + "," + magZSeparatedLine[i] + "," + temSeparatedLine[i]
                                        try {
                                            rawWriter?.append(allData)?.append("\n")
                                            rawWriter?.flush()
                                        } catch (e: IOException) {
                                            e.printStackTrace()
                                        }
                                        val allSeparatedData = allData.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                        val acc=ArrayList<Double>()
                                        acc.add(allSeparatedData[0].toDouble())
                                        acc.add(allSeparatedData[1].toDouble())
                                        acc.add(allSeparatedData[2].toDouble())

                                        if(init){
                                            gBias[0] = allSeparatedData[3].toDouble()
                                            gBias[1] = allSeparatedData[4].toDouble()
                                            gBias[2] = allSeparatedData[5].toDouble()
                                            init = false
                                        }

                                        val gyr=ArrayList<Double>()
                                        gyr.add(allSeparatedData[3].toDouble()-gBias[0])
                                        gyr.add(allSeparatedData[4].toDouble()-gBias[1])
                                        gyr.add(allSeparatedData[5].toDouble()-gBias[2])

//                                        yawGyr += 0.04*(gyr[1])
//                                        tvHeading?.text= (yawGyr*180/Math.PI).toString()
                                        // ----- Correct Magnetometer
                                        // offset_x = (max(x) + min(x)) / 2
                                        // avg_delta_x = (max(x) - min(x)) / 2
                                        // avg_delta = (avg_delta_x + avg_delta_y + avg_delta_z) / 3
                                        // scale_x = avg_delta / avg_delta_x
                                        // magCorrected_x = (sensor_x - offset_x) * scale_x
                                        // source = https://appelsiini.net/2018/calibrate-magnetometer/
                                        val mag=ArrayList<Double>()
                                        val offset=ArrayList<Double>()
                                        val avgDelta=ArrayList<Double>()
                                        mag.add(allSeparatedData[6].toDouble())
                                        mag.add(allSeparatedData[7].toDouble())
                                        mag.add(allSeparatedData[8].toDouble())
                                        for(iMag in 0..2){
                                            if(mag[iMag]>maxMag[iMag]){
                                                maxMag[iMag]=mag[iMag]
                                            }
                                            if(mag[iMag]<minMag[iMag]){
                                                minMag[iMag]=mag[iMag]
                                            }
                                            offset.add((maxMag[iMag]+minMag[iMag])/2)
                                            avgDelta.add((maxMag[iMag]-minMag[iMag])/2)
                                        }
                                        val allAvgDelta = (avgDelta[0]+avgDelta[1]+avgDelta[2])/3
                                        val scale=ArrayList<Double>()
                                        val magCorrected=ArrayList<Double>()
                                        for(iMag in 0..2){
                                            scale.add(allAvgDelta/avgDelta[iMag])
                                            magCorrected.add((mag[iMag]-offset[iMag])*scale[iMag])
                                        }
                                        // ----- End

                                        // Tilt-compensate
                                        val magX = magCorrected[0]*cos(pitch) + magCorrected[2]*sin(pitch)
                                        val magY = magCorrected[0]*sin(roll)*sin(pitch) + magCorrected[1]*cos(roll) - magCorrected[2]*sin(roll)*cos(pitch)
                                        val yaw=atan2(magY,magX)
//                                        if(yaw<0){
//                                            yaw += 6.28319
//                                        }
                                        orientation[2]=yaw
//                                        showSensorData(arrayListOf(minMag[0],minMag[1],minMag[2]))
                                        when(process){
                                            0->{
                                                isStart(gyr, accM1, gyrM1, temM1)
                                            }
                                            1->{
                                                collectData(accM1,gyrM1,temM1)
                                                isMax(acc[0],accM1[0])
                                            }
                                            2->{
                                                collectData(accM1,gyrM1,temM1)
                                                isMin(acc[0],accM1[0])
                                            }
                                            3->{
                                                collectData(accM1,gyrM1,temM1)
                                                isStop(gyr[2],gyrM1[2])
                                            }
                                            4->{
                                                var iEndBuffHeading = 15
                                                while(iEndBuffHeading>0){
                                                    buffEndHeading += yaw
                                                    iEndBuffHeading--
                                                }
                                                computeAll()
                                                process=0
                                            }
                                        }
                                        when(dataNum){
                                            0->{
                                                showSensorData(acc)
                                            }
                                            1->{
                                                showSensorData(gyr)
                                            }
                                            2->{
                                                showSensorData(magCorrected)
                                            }
                                            3->{
                                                val degOrientation = arrayListOf<Double>()
                                                for(value in orientation){
                                                    degOrientation.add(value*180/Math.PI)
                                                }
                                                showSensorData(degOrientation)
                                            }
                                        }
                                        accM1=acc
                                        gyrM1=gyr
                                        temM1=allSeparatedData[9].toDouble()
                                    }
                                }
                            }
                        }
                    }
                    override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {}
                })
            }
            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {}
        }
    }

    @SuppressLint("SetTextI18n")
    fun isMax(acc_x:Double, acc_x_m1:Double){
        val accXThold=2.0
        if(acc_x>accXThold) {
            if (acc_x_m1 > accXThold){
                if (acc_x < acc_x_m1) {
                    strideCount++
                    process=2
                }
            }
        }
        else{
            if(acc_x_m1>accXThold){
                strideCount++
                process=2
            }
        }
    }

    fun isMin(acc_x:Double,acc_x_m1:Double){
        val accXThold=-2.0
        if(acc_x<accXThold){
            if(acc_x_m1<accXThold){
                if(acc_x>acc_x_m1){
                    process=3
                }
            }
        }
        else
            if(acc_x_m1<accXThold){
                process=3
            }
    }

    @SuppressLint("SetTextI18n")
    fun isStart(_gyr:ArrayList<Double>, _acc_m1:ArrayList<Double>, _gyr_m1:ArrayList<Double>, _temp:Double) {
        if(abs(_gyr[2])>2) {
            process = 1
            collectData(_acc_m1,_gyr_m1,_temp)
        }
    }

    @SuppressLint("SetTextI18n")
    fun isStop(_gyr_z:Double, _gyr_z_m1:Double) {
        if(abs(_gyr_z)<0.1 && abs(_gyr_z_m1)<0.1) {
            process=4
        }
    }

    fun collectData(_acc_m1:ArrayList<Double>,_gyr_m1:ArrayList<Double>,_temp:Double){
        val accGyr = ArrayList<Double>()
        accGyr.addAll(_acc_m1)
        accGyr.addAll(_gyr_m1)
        buffAccGyr.add(accGyr)
        buffTemp+=_temp
        buffStrideFrequency++
    }

    @SuppressLint("SetTextI18n")
    fun computeAll(){
        strideFrequency = buffStrideFrequency
        val largest = ArrayList<Double>()
        val smallest= ArrayList<Double>()
        val sum = ArrayList<Double>()
        val sumVar = ArrayList<Double>()
        for (i in 0..5) {
            sum.add(0.0)
            sumVar.add(0.0)
            largest.add(buffAccGyr[0][i])
            smallest.add(buffAccGyr[0][i])
        }
        for (accGyrXYZ in buffAccGyr) {
            for ((i, value) in accGyrXYZ.withIndex()) {
                if (largest[i] < value) {
                    largest[i] = value
                }
                if (smallest[i] > value) {
                    smallest[i] = value
                }
                sum[i] += value
            }
        }
        for (i in 0..5) {
            accGyrMax.add(largest[i])
            accGyrMin.add(smallest[i])
            accGyrMean.add(sum[i] / strideFrequency)
        }
        for (accGyrXYZ in buffAccGyr) {
            for ((i, value) in accGyrXYZ.withIndex()) {
                sumVar[i] += (value - accGyrMean[i]).pow(2)
            }
        }
        for (i in 0..5){
            accGyrVar.add(sumVar[i]/(strideFrequency-1))
        }
        val temp = buffTemp/strideFrequency
        val endHeading = buffEndHeading/15
        arrHeading.add(endHeading*180/Math.PI)
        tvHeading?.text = (endHeading*180/Math.PI).toString()

        // === Forward Pass ANN
        val featuresRaw = arrayListOf<Double>()
        for (j in 0..2){
            featuresRaw.add(accGyrMax[j])
        }
        for (j in 0..2){
            featuresRaw.add(accGyrMin[j])
        }
        for (j in 0..2){
            featuresRaw.add(accGyrMean[j])
        }
        for (j in 0..2){
            featuresRaw.add(accGyrVar[j])
        }
        for (j in 3..5){
            featuresRaw.add(accGyrMax[j])
        }
        for (j in 3..5){
            featuresRaw.add(accGyrMin[j])
        }
        for (j in 3..5){
            featuresRaw.add(accGyrMean[j])
        }
        for (j in 3..5){
            featuresRaw.add(accGyrVar[j])
        }
        featuresRaw.add(strideFrequency.toDouble())
        featuresRaw.add(temp)
        val features = arrayListOf<Double>()
        for((index, featureRaw) in featuresRaw.withIndex()){
            val feature = (featureRaw-mean[index][0].toDouble())/std[index][0].toDouble()
            features.add(feature)
        }
        var sumOutWeight = 0.0
        for (i in 0..3){
            var sumLayerWeight = 0.0
            for((index,feature) in features.withIndex()){
                sumLayerWeight += feature*wih1[i][index].toDouble()
            }
            val netH1 = bih1[i][0].toDouble() + sumLayerWeight
            val outH1 = max(0.0, netH1)
            sumOutWeight += outH1*wo[0][i].toDouble()
        }
        val netO = bo[0][0].toDouble() + sumOutWeight
        arrStrideLength.add(netO)
        etStrideLength?.text = "%.3f".format(netO)
//        x += (netO)* sin(endHeading)
//        y += (netO)* cos(endHeading)
//        mseries?.appendData(DataPoint(x, y), true, 100)

        // === End of Forward Pass ANN

        if(isOnlineMode && isFollowerActive){
            Thread {
                while (iRobotWaypointCheck==iRobotWaypoint){
                    client!!.publish("PDR/Leader/Position",  MqttMessage((arrStrideLength[iRobotWaypointCheck].toInt().toString()+","+ arrHeading[iRobotWaypointCheck].toInt().toString()).toByteArray()))
                    Thread.sleep(800)
                }
                iRobotWaypointCheck++
            }.start()
        }

        // The data are sorted like these
        // AccMax - AccMin - AccMean - AccVar - GyrMax - GyrMin - GyrMean - GyrVar - StrideFreq - Temp - Heading - Out - X - Y
        readyData = accGyrMax[0].toString() + "," + accGyrMax[1].toString() + "," + accGyrMax[2].toString() + "," + accGyrMin[0].toString() + "," + accGyrMin[1].toString() + "," + accGyrMin[2].toString() + "," + accGyrMean[0].toString() + "," + accGyrMean[1].toString() + "," + accGyrMean[2].toString() + "," + accGyrVar[0].toString() + "," + accGyrVar[1].toString() + "," + accGyrVar[2].toString() + "," + accGyrMax[3].toString() + "," + accGyrMax[4].toString() + "," + accGyrMax[5].toString() + "," + accGyrMin[3].toString() + "," + accGyrMin[4].toString() + "," + accGyrMin[5].toString() + "," + accGyrMean[3].toString() + "," + accGyrMean[4].toString() + "," + accGyrMean[5].toString() + "," + accGyrVar[3].toString() + "," + accGyrVar[4].toString() + "," + accGyrVar[5].toString() + ",$strideFrequency,$temp,"+endHeading*180/Math.PI+",$netO"

        if (!isOnlineMode){
            client!!.publish("PDR/isStart",  MqttMessage("0".toByteArray()))    // Stop Measuring Data
            targetForm?.visibility = View.VISIBLE
        }
        else{
            processedWriter?.append(readyData)?.append("\n")
            processedWriter?.flush()
        }

        buffAccGyr.clear()
        accGyrMax.clear()
        accGyrMin.clear()
        accGyrMean.clear()
        accGyrVar.clear()
        buffStrideFrequency=0
        buffTemp=0.0
        buffEndHeading=0.0
        // End
    }

    override fun onDestroy() {
        super.onDestroy()
        File(Environment.getExternalStorageDirectory().absolutePath + File.separator + BrokerConstants.DEFAULT_MOQUETTE_STORE_MAP_DB_FILENAME).delete()
    }

    @SuppressLint("SetTextI18n")
    private fun showSensorData(data: ArrayList<Double>) {
        etData1?.text = "%.3f".format(data[0])
        etData2?.text = "%.3f".format(data[1])
        etData3?.text = "%.3f".format(data[2])
    }

}
