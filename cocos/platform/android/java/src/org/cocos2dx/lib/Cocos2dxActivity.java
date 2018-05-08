/****************************************************************************
Copyright (c) 2010-2013 cocos2d-x.org

http://www.cocos2d-x.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 ****************************************************************************/
package org.cocos2dx.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager.OnActivityResultListener;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import org.cocos2dx.lib.Cocos2dxHelper.Cocos2dxHelperListener;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLContext;

public abstract class Cocos2dxActivity extends Activity implements Cocos2dxHelperListener {
    // ===========================================================
    // Constants
    // ===========================================================

    private final static String TAG = Cocos2dxActivity.class.getSimpleName();

    // ===========================================================
    // Fields
    // ===========================================================
    
    private org.cocos2dx.lib.Cocos2dxGLSurfaceView mGLSurfaceView = null;
    private int[] mGLContextAttrs = null;
    private org.cocos2dx.lib.Cocos2dxHandler mHandler = null;
    private static Cocos2dxActivity sContext = null;
    private org.cocos2dx.lib.Cocos2dxVideoHelper mVideoHelper = null;
    private org.cocos2dx.lib.Cocos2dxWebViewHelper mWebViewHelper = null;
    private org.cocos2dx.lib.Cocos2dxEditBoxHelper mEditBoxHelper = null;
    private boolean hasFocus = false;
    private boolean showVirtualButton = false;

    public org.cocos2dx.lib.Cocos2dxGLSurfaceView getGLSurfaceView(){
        return  mGLSurfaceView;
    }

    public static Context getContext() {
        return sContext;
    }
    
    public void setKeepScreenOn(boolean value) {
        final boolean newValue = value;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGLSurfaceView.setKeepScreenOn(newValue);
            }
        });
    }

    public void setEnableVirtualButton(boolean value) {
        this.showVirtualButton = value;
    }

    protected void onLoadNativeLibraries() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            String libName = bundle.getString("android.app.lib_name");
            System.loadLibrary(libName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // ===========================================================
    // Constructors
    // ===========================================================

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Workaround in https://stackoverflow.com/questions/16283079/re-launch-of-activity-on-home-button-but-only-the-first-time/16447508
        if (!isTaskRoot()) {
            // Android launched another instance of the root activity into an existing task
            //  so just quietly finish and go away, dropping the user back into the activity
            //  at the top of the stack (ie: the last state of this task)
            finish();
            Log.w(TAG, "[Workaround] Ignore the activity started from icon!");
            return;
        }

        this.hideVirtualButton();

        onLoadNativeLibraries();

        sContext = this;
        this.mHandler = new org.cocos2dx.lib.Cocos2dxHandler(this);
        
        org.cocos2dx.lib.Cocos2dxHelper.init(this);
        
        this.mGLContextAttrs = getGLContextAttrs();
        this.init();

        if (mVideoHelper == null) {
            mVideoHelper = new org.cocos2dx.lib.Cocos2dxVideoHelper(this, mFrameLayout);
        }
        
        if(mWebViewHelper == null){
            mWebViewHelper = new org.cocos2dx.lib.Cocos2dxWebViewHelper(mFrameLayout);
        }

        if(mEditBoxHelper == null){
            mEditBoxHelper = new org.cocos2dx.lib.Cocos2dxEditBoxHelper(mFrameLayout);
        }

        Window window = this.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // Audio configuration
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        org.cocos2dx.lib.Cocos2dxEngineDataManager.init(this, mGLSurfaceView);
    }

    //native method,call GLViewImpl::getGLContextAttrs() to get the OpenGL ES context attributions
    private static native int[] getGLContextAttrs();

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    @Override
    protected void onResume() {
    	Log.d(TAG, "onResume()");
        super.onResume();
//        Cocos2dxAudioFocusManager.registerAudioFocusListener(this);
        this.hideVirtualButton();
       	resumeIfHasFocus();

        org.cocos2dx.lib.Cocos2dxEngineDataManager.resume();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
    	Log.d(TAG, "onWindowFocusChanged() hasFocus=" + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        
        this.hasFocus = hasFocus;
        resumeIfHasFocus();
    }
    
    private void resumeIfHasFocus() {
        if(hasFocus) {
            this.hideVirtualButton();
        	org.cocos2dx.lib.Cocos2dxHelper.onResume();
        	mGLSurfaceView.onResume();
        }
    }

    @Override
    protected void onPause() {
    	Log.d(TAG, "onPause()");
        super.onPause();
//        org.cocos2dx.lib.Cocos2dxAudioFocusManager.unregisterAudioFocusListener(this);
        org.cocos2dx.lib.Cocos2dxHelper.onPause();
        mGLSurfaceView.onPause();
        org.cocos2dx.lib.Cocos2dxEngineDataManager.pause();
    }
    
    @Override
    protected void onDestroy() {
//        org.cocos2dx.lib.Cocos2dxAudioFocusManager.unregisterAudioFocusListener(this);
        super.onDestroy();

        org.cocos2dx.lib.Cocos2dxEngineDataManager.destroy();
    }

    @Override
    public void showDialog(final String pTitle, final String pMessage) {
        Message msg = new Message();
        msg.what = org.cocos2dx.lib.Cocos2dxHandler.HANDLER_SHOW_DIALOG;
        msg.obj = new org.cocos2dx.lib.Cocos2dxHandler.DialogMessage(pTitle, pMessage);
        this.mHandler.sendMessage(msg);
    }
    
    @Override
    public void runOnGLThread(final Runnable pRunnable) {
        this.mGLSurfaceView.queueEvent(pRunnable);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        for (OnActivityResultListener listener : org.cocos2dx.lib.Cocos2dxHelper.getOnActivityResultListeners()) {
            listener.onActivityResult(requestCode, resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    protected org.cocos2dx.lib.ResizeLayout mFrameLayout = null;
    // ===========================================================
    // Methods
    // ===========================================================
    public void init() {
        
        // FrameLayout
        ViewGroup.LayoutParams framelayout_params =
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                       ViewGroup.LayoutParams.MATCH_PARENT);

        mFrameLayout = new org.cocos2dx.lib.ResizeLayout(this);

        mFrameLayout.setLayoutParams(framelayout_params);

        // Cocos2dxEditText layout
        ViewGroup.LayoutParams edittext_layout_params =
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                       ViewGroup.LayoutParams.WRAP_CONTENT);
        org.cocos2dx.lib.Cocos2dxEditBox edittext = new org.cocos2dx.lib.Cocos2dxEditBox(this);
        edittext.setLayoutParams(edittext_layout_params);


        mFrameLayout.addView(edittext);

        // Cocos2dxGLSurfaceView
        this.mGLSurfaceView = this.onCreateView();

        // ...add to FrameLayout
        mFrameLayout.addView(this.mGLSurfaceView);

        // Switch to supported OpenGL (ARGB888) mode on emulator
        if (isAndroidEmulator())
           this.mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        this.mGLSurfaceView.setCocos2dxRenderer(new org.cocos2dx.lib.Cocos2dxRenderer());
        this.mGLSurfaceView.setCocos2dxEditText(edittext);

        // Set framelayout as the content view
        setContentView(mFrameLayout);
    }

    
    public org.cocos2dx.lib.Cocos2dxGLSurfaceView onCreateView() {
        org.cocos2dx.lib.Cocos2dxGLSurfaceView glSurfaceView = new org.cocos2dx.lib.Cocos2dxGLSurfaceView(this);
        //this line is need on some device if we specify an alpha bits
        if(this.mGLContextAttrs[3] > 0) glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        // use custom EGLConfigureChooser
        Cocos2dxEGLConfigChooser chooser = new Cocos2dxEGLConfigChooser(this.mGLContextAttrs);
        glSurfaceView.setEGLConfigChooser(chooser);

        return glSurfaceView;
    }

    protected void hideVirtualButton() {
        if (showVirtualButton) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 19) {
            // use reflection to remove dependence of API level

            Class viewClass = View.class;

            try {
                final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = org.cocos2dx.lib.Cocos2dxReflectionHelper.<Integer>getConstantValue(viewClass, "SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION");
                final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = org.cocos2dx.lib.Cocos2dxReflectionHelper.<Integer>getConstantValue(viewClass, "SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN");
                final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = org.cocos2dx.lib.Cocos2dxReflectionHelper.<Integer>getConstantValue(viewClass, "SYSTEM_UI_FLAG_HIDE_NAVIGATION");
                final int SYSTEM_UI_FLAG_FULLSCREEN = org.cocos2dx.lib.Cocos2dxReflectionHelper.<Integer>getConstantValue(viewClass, "SYSTEM_UI_FLAG_FULLSCREEN");
                final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = org.cocos2dx.lib.Cocos2dxReflectionHelper.<Integer>getConstantValue(viewClass, "SYSTEM_UI_FLAG_IMMERSIVE_STICKY");
                final int SYSTEM_UI_FLAG_LAYOUT_STABLE = org.cocos2dx.lib.Cocos2dxReflectionHelper.<Integer>getConstantValue(viewClass, "SYSTEM_UI_FLAG_LAYOUT_STABLE");

                // getWindow().getDecorView().setSystemUiVisibility();
                final Object[] parameters = new Object[]{SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | SYSTEM_UI_FLAG_IMMERSIVE_STICKY};
                org.cocos2dx.lib.Cocos2dxReflectionHelper.<Void>invokeInstanceMethod(getWindow().getDecorView(),
                        "setSystemUiVisibility",
                        new Class[]{Integer.TYPE},
                        parameters);
            } catch (NullPointerException e) {
                Log.e(TAG, "hideVirtualButton", e);
            }
        }
    }

   private static boolean isAndroidEmulator() {
      String model = Build.MODEL;
      Log.d(TAG, "model=" + model);
      String product = Build.PRODUCT;
      Log.d(TAG, "product=" + product);
      boolean isEmulator = false;
      if (product != null) {
         isEmulator = product.equals("sdk") || product.contains("_sdk") || product.contains("sdk_");
      }
      Log.d(TAG, "isEmulator=" + isEmulator);
      return isEmulator;
   }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    private class Cocos2dxEGLConfigChooser implements GLSurfaceView.EGLConfigChooser
    {
        private int[] mConfigAttributes;
        private  final int EGL_OPENGL_ES2_BIT = 0x04;
        private  final int EGL_OPENGL_ES3_BIT = 0x40;
        public Cocos2dxEGLConfigChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize, int stencilSize)
        {
            mConfigAttributes = new int[] {redSize, greenSize, blueSize, alphaSize, depthSize, stencilSize};
        }
        public Cocos2dxEGLConfigChooser(int[] attributes)
        {
            mConfigAttributes = attributes;
        }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display)
        {
            int[][] EGLAttributes = {
                {
                    // GL ES 2 with user set
                    EGL10.EGL_RED_SIZE, mConfigAttributes[0],
                    EGL10.EGL_GREEN_SIZE, mConfigAttributes[1],
                    EGL10.EGL_BLUE_SIZE, mConfigAttributes[2],
                    EGL10.EGL_ALPHA_SIZE, mConfigAttributes[3],
                    EGL10.EGL_DEPTH_SIZE, mConfigAttributes[4],
                    EGL10.EGL_STENCIL_SIZE, mConfigAttributes[5],
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_NONE
                },
                {
                     // GL ES 2 with user set
                     EGL10.EGL_RED_SIZE, mConfigAttributes[0],
                     EGL10.EGL_GREEN_SIZE, mConfigAttributes[1],
                     EGL10.EGL_BLUE_SIZE, mConfigAttributes[2],
                     EGL10.EGL_ALPHA_SIZE, mConfigAttributes[3],
                     EGL10.EGL_DEPTH_SIZE, mConfigAttributes[4] >= 24 ? 16 : mConfigAttributes[4],
                     EGL10.EGL_STENCIL_SIZE, mConfigAttributes[5],
                     EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                     EGL10.EGL_NONE
                },
                {
                    // GL ES 2 by default
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_NONE
                }
            };

            EGLConfig result = null;
            for (int[] eglAtribute : EGLAttributes) {
                result = this.doChooseConfig(egl, display, eglAtribute);
                if (result != null)
                    return result;
            }

            Log.e(DEVICE_POLICY_SERVICE, "Can not select an EGLConfig for rendering.");
            return null;
        }

        private EGLConfig doChooseConfig(EGL10 egl, EGLDisplay display, int[] attributes) {
            EGLConfig[] configs = new EGLConfig[1];
            int[] matchedConfigNum = new int[1];
            boolean result = egl.eglChooseConfig(display, attributes, configs, 1, matchedConfigNum);
            if (result && matchedConfigNum[0] > 0) {
                return configs[0];
            }
            return null;
        }
    }
}
