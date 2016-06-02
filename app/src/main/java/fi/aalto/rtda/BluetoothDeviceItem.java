package fi.aalto.rtda;

import android.bluetooth.BluetoothDevice;

/**
 * Created by zhangy17 on 02/06/16.
 */
public class BluetoothDeviceItem {
    /** The No. of the Bluetooth Device */
    private int btDeviceNo;

    /** Bluetooth Device Object */
    private BluetoothDevice btDevice;

    /** Whether this BT device is allowed device */
    private boolean isAllowedDevice;
    /** Whether this BT device is irrelevant device */
    private boolean isUselessDevice;
    /** Whether this BT device currently in range */
    private boolean isCurrentDevice;
    /** Whether this BT device is still in range */
    private boolean inScope;
    /** The type of this BT device */
    private int btMajorDeviceClass;

    public void setBTdeviceNo(int no)
    {
        btDeviceNo = no;
    }
    public void setBluetoothDevice(BluetoothDevice device)
    {
        btDevice = device;
    }
    public void setIsUseless(boolean isUseless)
    {
        isUselessDevice = isUseless;
    }
    public void setIsAllowed(boolean isAllowed)
    {
        isAllowedDevice = isAllowed;
    }
    public void setIsCurrent(boolean isCurrent)
    {
        isCurrentDevice = isCurrent;
    }
    public void setIsInScope(boolean isInScope)
    {
        inScope = isInScope;
    }

    public void setBTMajorDeviceClass(int majorClass)
    {
        btMajorDeviceClass = majorClass;
    }
    public int getBTDeviceNo()
    {
        return btDeviceNo;
    }
    public BluetoothDevice getBluetoothDevice()
    {
        return btDevice;
    }
    public boolean getIsUseless()
    {
        return isUselessDevice;
    }
    public boolean getIsAllowed()
    {
        return isAllowedDevice;
    }
    public boolean getIsCurrent()
    {
        return isCurrentDevice;
    }
    public boolean getIsInScope()
    {
        return inScope;
    }
    public int getBTMajorDeviceClass()
    {
        return btMajorDeviceClass;
    }
}
