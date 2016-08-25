
package android.app;  
import android.os.Handler;  
import android.os.RemoteException;  
import android.util.Log; 
 
/** {@hide} */
public class DeviceManager  
{  
        public static final String TAG = "DeviceManager";  
        IDeviceManager mService;  
        Handler mHandler;  
  
        public DeviceManager(IDeviceManager service, Handler handler) {  
                mService = service;  
                mHandler = handler;  
        }  
        /** @hide */        
        public String getValue(String name){  
            try {  
                return mService.getValue(name);  
            } catch (RemoteException e) {  
                Log.e(TAG, "[getValue] RemoteException");  
            }  
            return null;  
        }  
        /** @hide */
        public int update(String name, String value, int attribute) {  
            try {  
                return mService.update(name, value, attribute);  
            } catch (RemoteException e) {  
                Log.e(TAG, "[update] RemoteException");  
            }  
            return -1;  
        }  
}  
