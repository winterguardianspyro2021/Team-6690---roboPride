package frc.robot;

import com.ctre.phoenix.sensors.AbsoluteSensorRange;
import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix.sensors.CANCoderConfiguration;
import com.ctre.phoenix.sensors.SensorInitializationStrategy;
import com.ctre.phoenix.sensors.SensorTimeBase;
import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
//import edu.wpi.first.wpilibj.Timer;
import frc.lib.util.SwerveModuleConstants;
import frc.robot.Constants.Drivebase;

public class SwerveModule {
    public int moduleNumber;
    private double angleOffset, lastAngle;
    private CANSparkMax angleMotor, driveMotor;
    private CANCoder absoluteEncoder;
    private RelativeEncoder angleEncoder, driveEncoder;
    private SparkMaxPIDController angleController, driveController;

    SimpleMotorFeedforward feedforward = new SimpleMotorFeedforward(Drivebase.KS, Drivebase.KV, Drivebase.KA);

    public SwerveModule(int moduleNumber, SwerveModuleConstants moduleConstants) {
        this.moduleNumber = moduleNumber;
        angleOffset = moduleConstants.angleOffset;

        angleMotor = new CANSparkMax(moduleConstants.angleMotorID, MotorType.kBrushless);
        driveMotor = new CANSparkMax(moduleConstants.driveMotorID, MotorType.kBrushless);
        angleMotor.restoreFactoryDefaults();
        driveMotor.restoreFactoryDefaults();
        driveMotor.setOpenLoopRampRate(0.5);

        // Config angle encoders
        absoluteEncoder = new CANCoder(moduleConstants.cancoderID);
        absoluteEncoder.configFactoryDefault();
        CANCoderConfiguration canCoderConfiguration = new CANCoderConfiguration();
        canCoderConfiguration.absoluteSensorRange = AbsoluteSensorRange.Unsigned_0_to_360;
        canCoderConfiguration.sensorDirection = Drivebase.CANCODER_INVERT;
        canCoderConfiguration.initializationStrategy = SensorInitializationStrategy.BootToAbsolutePosition;
        canCoderConfiguration.sensorTimeBase = SensorTimeBase.PerSecond;
        absoluteEncoder.configAllSettings(canCoderConfiguration);

        angleEncoder = angleMotor.getEncoder();
        angleEncoder.setPositionConversionFactor(Drivebase.DEGREES_PER_STEERING_ROTATION);
        angleEncoder.setVelocityConversionFactor(Drivebase.DEGREES_PER_STEERING_ROTATION / 60);
        angleEncoder.setPosition(absoluteEncoder.getAbsolutePosition() - angleOffset);

        // Config angle motor/controller
        angleController = angleMotor.getPIDController();
        angleController.setP(Drivebase.MODULE_KP);
        angleController.setI(Drivebase.MODULE_KI);
        angleController.setD(Drivebase.MODULE_KD);
        angleController.setFF(Drivebase.MODULE_KF);
        angleController.setIZone(Drivebase.MODULE_IZ);
        angleController.setPositionPIDWrappingEnabled(true);
        angleController.setPositionPIDWrappingMaxInput(180);
        angleController.setPositionPIDWrappingMinInput(-180);
        angleMotor.setIdleMode(CANSparkMax.IdleMode.kBrake); 
        angleMotor.setSmartCurrentLimit(Constants.Drivebase.Angle_Motor_Limit);

        // Config drive motor/controller
        driveController = driveMotor.getPIDController();
        driveEncoder = driveMotor.getEncoder();
        driveEncoder.setPositionConversionFactor(Drivebase.METERS_PER_MOTOR_ROTATION);
        driveEncoder.setVelocityConversionFactor(Drivebase.METERS_PER_MOTOR_ROTATION / 60);
        driveController.setP(Drivebase.VELOCITY_KP);
        driveController.setI(Drivebase.VELOCITY_KI);
        driveController.setD(Drivebase.VELOCITY_KD);
        driveController.setFF(Drivebase.VELOCITY_KF);
        driveController.setIZone(Drivebase.VELOCITY_IZ);
        driveMotor.setInverted(Drivebase.DRIVE_MOTOR_INVERT);
        driveMotor.setIdleMode(CANSparkMax.IdleMode.kBrake); 
        driveMotor.setSmartCurrentLimit(Constants.Drivebase.Drive_Motor_Limit);

        //driveMotor.burnFlash();
        //angleMotor.burnFlash();

        
            
        lastAngle = getState().angle.getDegrees();
    }
 
    public void setMotor(double speed){ 
        angleEncoder.setPosition(speed);
    }

    public void setDriveSpeed(double speed){
        driveMotor.set(speed);
    }
    public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
        desiredState = SwerveModuleState.optimize(desiredState, getState().angle);

        if (isOpenLoop) {
            double percentOutput = desiredState.speedMetersPerSecond / Drivebase.MAX_SPEED;
            driveMotor.set(percentOutput);
        } else {
            double velocity = desiredState.speedMetersPerSecond;
            driveController.setReference(velocity, ControlType.kVelocity, 0, feedforward.calculate(velocity));
        }

        double angle = (Math.abs(desiredState.speedMetersPerSecond) <= (Drivebase.MAX_SPEED * 0.01) ? 
            lastAngle :
            desiredState.angle.getDegrees()); // Prevents module rotation if speed is less than 1%
        angleController.setReference(angle, ControlType.kPosition);
        lastAngle = angle; 
    }

    public SwerveModuleState getState() {
        double velocity = driveEncoder.getVelocity();
        Rotation2d azimuth = Rotation2d.fromDegrees(angleEncoder.getPosition());
        return new SwerveModuleState(velocity, azimuth);
    }

    public SwerveModulePosition getPosition() {
        double position = driveEncoder.getPosition();
        Rotation2d azimuth = Rotation2d.fromDegrees(angleEncoder.getPosition());
        return new SwerveModulePosition(position, azimuth);
    }

    public double getCANCoder() {
        return absoluteEncoder.getAbsolutePosition();
    }

    public double getRelativeEncoder() {
        return angleEncoder.getPosition();
    }

    public void setMotorBrake(boolean brake) {
        driveMotor.setIdleMode(brake ? CANSparkMax.IdleMode.kBrake : CANSparkMax.IdleMode.kCoast);
    }

}
