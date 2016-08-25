
package android.app;  
/** @hide */  
interface IDeviceManager  
{  
    String getValue(String name);  
    int update(String name, String value, int attribute);  
}  
