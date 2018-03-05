package com.sosee.mysenorr.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import com.sosee.mysenorr.representation.Quaternion;


/**
 * The orientation provider that delivers the absolute orientation from the {@link Sensor#TYPE_GYROSCOPE
 * Gyroscope} and {@link Sensor#TYPE_ROTATION_VECTOR Android Rotation Vector sensor}.
 * <p/>
 * It mainly relies on the gyroscope, but corrects with the Android Rotation Vector which also provides an absolute
 * estimation of current orientation. The correction is a static weight.
 *
 * @author Alexander Pacha
 * 从传感器＃TYPE_GYROSCOPE传送绝对方向的方向提供程序
 * 陀螺仪}和{@link Sensor ＃TYPE_ROTATION_VECTOR Android旋转矢量传感器}。
 * 它主要依赖于陀螺仪，但可以纠正与Android旋转矢量，这也提供了一个绝对的
 * 估计当前的方向。 修正是一个静态的权重。
 */
public class ImprovedOrientationSensor2Provider extends OrientationProvider {

    /**
     * Constant specifying the factor between a Nano-second and a second
     * 指定纳米秒和秒之间的因子的常量
     */
    private static final float NS2S = 1.0f / 1000000000.0f;
    /**
     * This is a filter-threshold for discarding Gyroscope measurements that are below a certain level and
     * potentially are only noise and not real motion. Values from the gyroscope are usually between 0 (stop) and
     * 10 (rapid rotation), so 0.1 seems to be a reasonable threshold to filter noise (usually smaller than 0.1) and
     * real motion (usually > 0.1). Note that there is a chance of missing real motion, if the use is turning the
     * device really slowly, so this value has to find a balance between accepting noise (threshold = 0) and missing
     * slow user-action (threshold > 0.5). 0.1 seems to work fine for most applications.
     *
     * 这是用于丢弃低于特定水平的陀螺仪测量的滤波器阈值
     *可能只是噪音而不是真正的运动。 来自陀螺仪的数值通常在0（停止）和
     * 10（快速旋转），所以0.1似乎是一个合理的阈值来过滤噪声（通常小于0.1）和
     *实际运动（通常> 0.1）。 请注意，如果使用正在转向，则有可能丢失真实的动作
     *设备真的很慢，所以此值必须在接受噪声（阈值= 0）和丢失之间找到平衡点
     *用户行为缓慢（阈值> 0.5）。 0.1对大多数应用程序似乎工作正常。
     */
    private static final double EPSILON = 0.1f;
    /**
     * This weight determines indirectly how much the rotation sensor will be used to correct. This weight will be
     * multiplied by the velocity to obtain the actual weight. (in sensor-fusion-scenario 2 -
     * SensorSelection.GyroscopeAndRotationVector2).
     * Must be a value between 0 and approx. 0.04 (because, if multiplied with a velocity of up to 25, should be still
     * less than 1, otherwise the SLERP will not correctly interpolate). Should be close to zero.
     *
     * 这个重量间接决定了旋转传感器将被用来纠正多少。 这个重量会是
     * 乘以速度以获得实际重量。 （在传感器融合场景2中 -
     * SensorSelection.GyroscopeAndRotationVector2）。
     * 必须是介于0和大约之间的值。 0.04（因为如果乘以速度高达25，应该仍然是
     * 小于1，否则SLERP将无法正确插入）。 应该接近于零。
     */
    private static final float INDIRECT_INTERPOLATION_WEIGHT = 0.01f;
    /**
     * The threshold that indicates an outlier of the rotation vector. If the dot-product between the two vectors
     * (gyroscope orientation and rotationVector orientation) falls below this threshold (ideally it should be 1,
     * if they are exactly the same) the system falls back to the gyroscope values only and just ignores the
     * rotation vector.
     * <p/>
     * This value should be quite high (> 0.7) to filter even the slightest discrepancies that causes jumps when
     * tiling the device. Possible values are between 0 and 1, where a value close to 1 means that even a very small
     * difference between the two sensors will be treated as outlier, whereas a value close to zero means that the
     * almost any discrepancy between the two sensors is tolerated.
     *
     * 指示旋转向量的离群值的阈值。 如果两个向量之间的点积
     *（陀螺仪方向和rotationVector方向）低于此阈值（理想情况下，它应该是1，
     *如果它们完全相同），则系统只会回到陀螺仪值，而忽略该值
     *旋转矢量。
     * <p />
     *这个值应该很高（> 0.7）来过滤即使是最小的差异，导致跳跃时
     *平铺设备。 可能的值在0和1之间，其中接近1的值意味着甚至非常小
     *两个传感器之间的差异将被视为异常值，而接近零的值意味着该值
     *两个传感器之间的几乎任何差异都是可以接受的。
     */
    private static final float OUTLIER_THRESHOLD = 0.85f;
    /**
     * The threshold that indicates a massive discrepancy between the rotation vector and the gyroscope orientation.
     * If the dot-product between the two vectors
     * (gyroscope orientation and rotationVector orientation) falls below this threshold (ideally it should be 1, if
     * they are exactly the same), the system will start increasing the panic counter (that probably indicates a
     * gyroscope failure).
     * <p/>
     * This value should be lower than OUTLIER_THRESHOLD (0.5 - 0.7) to only start increasing the panic counter,
     * when there is a huge discrepancy between the two fused sensors.
     * <p>
     * 表示旋转向量与陀螺仪方向之间存在巨大差异的阈值。
     *如果两个向量之间的点积
     *（陀螺仪方向和rotationVector方向）低于此阈值（理想情况下，它应该是1，如果
     *他们完全一样），系统将开始增加恐慌计数器（可能表示一个
     *陀螺仪故障）。
     * <p />
     *这个值应该低于OUTLIER_THRESHOLD（0.5 - 0.7）才开始增加恐慌计数器，
     *两个电容传感器之间存在巨大差异时。
     */
    private static final float OUTLIER_PANIC_THRESHOLD = 0.75f;
    /**
     * The threshold that indicates that a chaos state has been established rather than just a temporary peak in the
     * rotation vector (caused by exploding angled during fast tilting).
     * <p/>
     * If the chaosCounter is bigger than this threshold, the current position will be reset to whatever the
     * rotation vector indicates.
     * <p>
     * 表明混乱状态已经建立的阈值，而不仅仅是一个临时峰值
     *旋转矢量（在快速倾斜过程中由爆炸角度引起）。
     *如果chaosCounter大于此阈值，当前位置将被重置为任何值
     *旋转矢量表示。
     */
    private static final int PANIC_THRESHOLD = 60;
    private static float[] RMatrixRemapped = new float[16];
    private static float[] orientation = new float[3];
    /**
     * The quaternion that stores the difference that is obtained by the gyroscope.
     * Basically it contains a rotational difference encoded into a quaternion.
     * <p/>
     * To obtain the absolute orientation one must add this into an initial position by
     * multiplying it with another quaternion
     * <p>
     * 存储由陀螺仪获得的差异的四元数。
     *基本上它包含编码成四元数的旋转差异。
     *要获得绝对定向，必须将其添加到初始位置
     *乘以另一个四元数
     */
    private final Quaternion deltaQuaternion = new Quaternion();
    /**
     * The Quaternions that contain the current rotation (Angle and axis in Quaternion format) of the Gyroscope
     * <p>
     * 包含陀螺仪当前旋转（四角形格式的角度和轴）的四元数
     */
    private Quaternion quaternionGyroscope = new Quaternion();
    /**
     * The quaternion that contains the absolute orientation as obtained by the rotationVector sensor.
     * <p>
     * 包含rotationVector传感器获取的绝对方位的四元数
     */
    private Quaternion quaternionRotationVector = new Quaternion();
    /**
     * The time-stamp being used to record the time when the last gyroscope event occurred.
     * <p>
     * 时间标记用于记录最后一次陀螺仪事件发生的时间。
     */
    private long timestamp;
    /**
     * Value giving the total velocity of the gyroscope (will be high, when the device is moving fast and low when
     * the device is standing still). This is usually a value between 0 and 10 for normal motion. Heavy shaking can
     * increase it to about 25. Keep in mind, that these values are time-depended, so changing the sampling rate of
     * the sensor will affect this value!
     * <p>
     * 赋予陀螺仪总速度的值（当设备移动快而且速度低时，将会很高
     *设备静止不动）。 对于正常运动，这通常是介于0和10之间的值。 重摇可以
     *将其增加到大约25.请记住，这些值是时间依赖的，所以改变采样率
     *传感器会影响这个值！
     */
    private double gyroscopeRotationVelocity = 0;
    /**
     * Flag indicating, whether the orientations were initialised from the rotation vector or not. If false, the
     * gyroscope can not be used (since it's only meaningful to calculateAzimuth differences from an initial state). If
     * true,
     * the gyroscope can be used normally.
     * <p>
     * 标记指示方位是否从旋转矢量初始化。 如果是错误的，
     * 陀螺仪不能使用（因为它只对计算初始状态的方位差有意义）。 如果
     * 真实的，
     * 陀螺仪可以正常使用。
     */
    private boolean positionInitialised = false;
    /**
     * Counter that sums the number of consecutive frames, where the rotationVector and the gyroscope were
     * significantly different (and the dot-product was smaller than 0.7). This event can either happen when the
     * angles of the rotation vector explode (e.g. during fast tilting) or when the device was shaken heavily and
     * the gyroscope is now completely off.
     * <p>
     * 计数器将旋转矢量和陀螺仪所在的连续帧数相加
     *显着不同（并且点积小于0.7）。 这个事件可以发生的时候
     *旋转矢量的角度发生爆炸（例如在快速倾斜时）或装置剧烈摇晃时
     *陀螺仪现在完全关闭。
     */
    private int panicCounter;


    /**
     * Initialises a new ImprovedOrientationSensor2Provider
     * 初始化一个新的ImprovedOrientationSensor2Provider
     * @param sensorManager The android sensor manager
     */
    public ImprovedOrientationSensor2Provider(SensorManager sensorManager) {
        super(sensorManager);

        //Add the gyroscope and rotation Vector
        //添加陀螺仪和旋转矢量
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // Process rotation vector (just safe it)
            //过程旋转矢量（只是安全）

            float[] q = new float[4];
            // Calculate angle. Starting with API_18, Android will provide this value as event.values[3], but if not, we have to calculateAzimuth it manually.
            //计算角度。 从API_18开始，Android将提供这个值作为event.values [3]，但如果不是，我们必须手动计算出Azimuth
            SensorManager.getQuaternionFromVector(q, event.values);

            // Store in quaternion
            //以四元数存储
            quaternionRotationVector.setXYZW(q[1], q[2], q[3], -q[0]);
            if (!positionInitialised) {
                // Override
                quaternionGyroscope.set(quaternionRotationVector);
                positionInitialised = true;
            }

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // Process Gyroscope and perform fusion
            //处理陀螺仪并执行融合

            // This timestep's delta rotation to be multiplied by the current rotation
            //此时间步的增量旋转将乘以当前旋转

            // after computing it from the gyro sample data.
            //从陀螺样本数据中计算出来之后。
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2S;
                // Axis of the rotation sample, not normalized yet.
                //旋转样本的轴尚未标准化。
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];

                // Calculate the angular speed of the sample
                //计算样品的角速度
                gyroscopeRotationVelocity = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

                // Normalize the rotation vector if it's big enough to get the axis
                //如果旋转矢量足够大以便获取轴，则将其标准化
                if (gyroscopeRotationVelocity > EPSILON) {
                    axisX /= gyroscopeRotationVelocity;
                    axisY /= gyroscopeRotationVelocity;
                    axisZ /= gyroscopeRotationVelocity;
                }

                // Integrate around this axis with the angular speed by the timestep
                //通过时间步长围绕该轴进行角速度积分

                // in order to get a delta rotation from this sample over the timestep
                //以便在时间步骤上从这个样本获得旋转角度

                // We will convert this axis-angle representation of the delta rotation
                //我们将转换此旋转角度的轴角表示

                // into a quaternion before turning it into the rotation matrix.
                //转换成四元数，然后将其转换为旋转矩阵。
                double thetaOverTwo = gyroscopeRotationVelocity * dT / 2.0f;
                double sinThetaOverTwo = Math.sin(thetaOverTwo);
                double cosThetaOverTwo = Math.cos(thetaOverTwo);
                deltaQuaternion.setX((float) (sinThetaOverTwo * axisX));
                deltaQuaternion.setY((float) (sinThetaOverTwo * axisY));
                deltaQuaternion.setZ((float) (sinThetaOverTwo * axisZ));
                deltaQuaternion.setW(-(float) cosThetaOverTwo);

                // Move current gyro orientation
                //移动当前的陀螺仪方向
                deltaQuaternion.multiplyByQuat(quaternionGyroscope, quaternionGyroscope);

                // Calculate dot-product to calculateAzimuth whether the two orientation sensors have diverged
                //计算点积来计算方位角是否两个方位传感器已经发散
                // (if the dot-product is closer to 0 than to 1), because it should be close to 1 if both are the same.
                //（如果点积比0更接近1），因为如果两者相同，它应该接近1
                float dotProd = quaternionGyroscope.dotProduct(quaternionRotationVector);

                // If they have diverged, rely on gyroscope only (this happens on some devices when the rotation vector "jumps").
                //如果它们分开，只能依靠陀螺仪（当旋转矢量“跳跃”时，会发生在某些设备上）。
                if (Math.abs(dotProd) < OUTLIER_THRESHOLD) {
                    // Increase panic counter
                    if (Math.abs(dotProd) < OUTLIER_PANIC_THRESHOLD) {
                        panicCounter++;
                    }

                    // Directly use Gyro
                    //增加恐慌计数器
                    setOrientationQuaternionAndMatrix(quaternionGyroscope);

                } else {
                    // Both are nearly saying the same. Perform normal fusion.
                    // Interpolate with a fixed weight between the two absolute quaternions obtained from gyro and rotation vector sensors
                    // The weight should be quite low, so the rotation vector corrects the gyro only slowly, and the output keeps responsive.

                    //两者几乎都是这样说的。 执行正常的融合。
                    // 用从陀螺仪和旋转矢量传感器获得的两个绝对四元数之间的固定权重进行插值
                    //重量应该很低，所以旋转矢量只能缓慢地修正陀螺仪，并且输出保持响应。

                    Quaternion interpolate = new Quaternion();
                    quaternionGyroscope.slerp(quaternionRotationVector, interpolate,
                            (float) (INDIRECT_INTERPOLATION_WEIGHT * gyroscopeRotationVelocity));

                    // Use the interpolated value between gyro and rotationVector
                    //使用陀螺仪和rotationVector之间的插值
                    setOrientationQuaternionAndMatrix(interpolate);
                    // Override current gyroscope-orientation
                    //覆盖当前的陀螺仪方向
                    quaternionGyroscope.copyVec4(interpolate);

                    // Reset the panic counter because both sensors are saying the same again
                    //重置恐慌计数器，因为两个传感器再次说相同
                    panicCounter = 0;
                }

                if (panicCounter > PANIC_THRESHOLD) {
                    //恐慌计数器大于阈值; 这表示陀螺仪失败。 恐慌重置即将发生。
                    Log.d("Rotation Vector",
                            "Panic counter is bigger than threshold; this indicates a Gyroscope failure. Panic reset is imminent.");

                    if (gyroscopeRotationVelocity < 3) {
                        //执行恐慌复位。 将方向重置为旋转矢量值。
                        Log.d("Rotation Vector",
                                "Performing Panic-reset. Resetting orientation to rotation-vector value.");

                        // Manually set position to whatever rotation vector says.
                        //手动设置位置，无论旋转向量如何。
                        setOrientationQuaternionAndMatrix(quaternionRotationVector);
                        // Override current gyroscope-orientation with corrected value
                        //用修正值覆盖当前的陀螺仪方向
                        quaternionGyroscope.copyVec4(quaternionRotationVector);

                        panicCounter = 0;
                    } else {
                        //由于正在进行的动作而导致恐慌重置延迟（用户仍在晃动设备）。 陀螺仪速度：％.2f> 3
                        Log.d("Rotation Vector",
                                String.format(
                                        "Panic reset delayed due to ongoing motion (user is still shaking the device). Gyroscope Velocity: %.2f > 3",
                                        gyroscopeRotationVelocity));
                    }
                }
            }
            timestamp = event.timestamp;
        }
    }

    /**
     * Sets the output quaternion and matrix with the provided quaternion and synchronises the setting
     * <p>
     * 使用提供的四元数设置输出四元数和矩阵并同步设置
     * @param quaternion The Quaternion to set (the result of the sensor fusion)
     */
    private void setOrientationQuaternionAndMatrix(Quaternion quaternion) {
        Quaternion correctedQuat = quaternion.clone();
        // We inverted w in the deltaQuaternion, because currentOrientationQuaternion required it.
        // Before converting it back to matrix representation, we need to revert this process

        //我们在deltaQuaternion中反转了w，因为currentOrientationQuaternion需要它。
        // 在将它转换回矩阵表示之前，我们需要恢复这个过程
        correctedQuat.w(-correctedQuat.w());

        synchronized (syncToken) {
            // Use gyro only
            //只能使用陀螺仪
            currentOrientationQuaternion.copyVec4(quaternion);
            // Set the rotation matrix as well to have both representations
            //设置旋转矩阵以同时具有两个表示
            SensorManager.getRotationMatrixFromVector(currentOrientationRotationMatrix.matrix, correctedQuat.ToArray());
        }
    }

    public float getAzimuth(float decl) {
        SensorManager.remapCoordinateSystem(currentOrientationRotationMatrix.matrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, RMatrixRemapped);
        SensorManager.getOrientation(RMatrixRemapped, orientation);

        Log.d("hyuny",orientation[0]+"");

        if (orientation[0] >= 0) {
            // Azimuth-Calculation (rad in degree) + difference to true north (decl)
            //方位角计算（rad in度）+与真北差（decl）
            return (orientation[0] * 57.29577951f + decl);
        } else {
            // Azimuth-Calculation (rad in degree) +360 + difference to true north (decl)
            //方位角 - 计算（rad度）+360 +与真北差（decl）
            return (orientation[0] * 57.29577951f + 360 + decl);
        }

    }
}
