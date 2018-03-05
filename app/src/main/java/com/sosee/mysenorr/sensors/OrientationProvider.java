/**
 *
 */
package com.sosee.mysenorr.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


import com.sosee.mysenorr.representation.EulerAngles;
import com.sosee.mysenorr.representation.Matrixf4x4;
import com.sosee.mysenorr.representation.Quaternion;

import java.util.ArrayList;
import java.util.List;

/**
 * Classes implementing this interface provide an orientation of the device
 * either by directly accessing hardware, using Android sensor fusion or fusing
 * sensors itself.
 * <p/>
 * The orientation can be provided as rotation matrix or quaternion.
 *
 *实现这个接口的类提供了设备的方向
 *通过直接访问硬件，使用Android传感器融合或融合传感器本身。
 *方向可以作为旋转矩阵或四元数提供。
 */
public abstract class OrientationProvider implements SensorEventListener {
    /**
     * Sync-token for syncing read/write to sensor-data from sensor manager and
     * fusion algorithm
     *
     * 用于同步读/写传感器管理器和传感器数据的同步令牌
     *融合算法
     */
    protected final Object syncToken = new Object();
    /**
     * The matrix that holds the current rotation
     *保存当前旋转的矩阵
     */
    protected final Matrixf4x4 currentOrientationRotationMatrix;
    /**
     * The quaternion that holds the current rotation
     * 保持当前旋转的四元数
     */
    protected final Quaternion currentOrientationQuaternion;
    /**
     * The list of sensors used by this provider
     * 提供商使用的传感器列表
     */
    protected List<Sensor> sensorList = new ArrayList<Sensor>();
    /**
     * The sensor manager for accessing android sensors
     * 用于访问android传感器的传感器管理器
     */
    protected SensorManager sensorManager;

    /**
     * Initialises a new OrientationProvider
     * 初始化一个新的定位提供程序
     * @param sensorManager The android sensor manager
     */
    public OrientationProvider(SensorManager sensorManager) {
        this.sensorManager = sensorManager;

        // Initialise with identity
        //以身份初始化
        currentOrientationRotationMatrix = new Matrixf4x4();

        // Initialise with identity
        currentOrientationQuaternion = new Quaternion();
    }

    /**
     * Starts the sensor fusion (e.g. when resuming the activity)
     * 开始传感器融合（例如，恢复活动时）
     */
    public void start() {
        // enable our sensor when the activity is resumed, ask for
        // 10 ms updates.
        //当活动恢复时启用我们的传感器，请求
        // 10 ms更新。
        for (Sensor sensor : sensorList) {
            // enable our sensors when the activity is resumed, ask for
            // 20 ms updates (Sensor_delay_game)
            //在活动恢复时启用我们的传感器，请求
            // 20 ms更新（Sensor_delay_game）
            sensorManager.registerListener(this, sensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /**
     * Stops the sensor fusion (e.g. when pausing/suspending the activity)
     * 停止传感器融合（例如，暂停/暂停活动时）
     */
    public void stop() {
        // make sure to turn our sensors off when the activity is paused
        //请确保在活动暂停时关闭传感器
        for (Sensor sensor : sensorList) {
            sensorManager.unregisterListener(this, sensor);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not doing anything
    }

    /**
     * @return Returns the current rotation of the device in the rotation matrix
     * format (4x4 matrix)
     * 返回旋转矩阵中设备的当前旋转
     *格式（4x4矩阵）
     *
     */
    public Matrixf4x4 getRotationMatrix() {
        synchronized (syncToken) {
            return currentOrientationRotationMatrix;
        }
    }

    /**
     * @return Returns the current rotation of the device in the quaternion
     * format (vector4f)
     *返回四元数中设备的当前旋转
     *格式（vector4f）
     */
    public Quaternion getQuaternion() {
        synchronized (syncToken) {
            return currentOrientationQuaternion.clone();
        }
    }

    /**
     * @return Returns the current rotation of the device in the Euler-Angles
     * 返回设备在欧拉角中的当前旋转
     */
    public EulerAngles getEulerAngles() {
        synchronized (syncToken) {

            float[] angles = new float[3];
            SensorManager.getOrientation(currentOrientationRotationMatrix.matrix, angles);
            return new EulerAngles(angles[0], angles[1], angles[2]);
        }
    }
}
