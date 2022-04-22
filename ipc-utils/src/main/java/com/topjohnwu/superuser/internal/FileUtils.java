/*
 * Copyright 2022 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.topjohnwu.superuser.internal;

import static android.os.ParcelFileDescriptor.MODE_APPEND;
import static android.os.ParcelFileDescriptor.MODE_CREATE;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.os.ParcelFileDescriptor.MODE_READ_WRITE;
import static android.os.ParcelFileDescriptor.MODE_TRUNCATE;
import static android.os.ParcelFileDescriptor.MODE_WRITE_ONLY;
import static android.system.OsConstants.ENOSYS;
import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Int64Ref;
import android.system.Os;
import android.util.MutableLong;

import androidx.annotation.RequiresApi;

import java.io.FileDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class FileUtils {

    private static Object os;
    private static Method splice;
    private static Method sendfile;

    static int pfdModeToPosix(int mode) {
        int res;
        if ((mode & MODE_READ_WRITE) == MODE_READ_WRITE) {
            res = O_RDWR;
        } else if ((mode & MODE_WRITE_ONLY) == MODE_WRITE_ONLY) {
            res = O_WRONLY;
        } else if ((mode & MODE_READ_ONLY) == MODE_READ_ONLY) {
            res = O_RDONLY;
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if ((mode & MODE_CREATE) == MODE_CREATE) {
            res |= O_CREAT;
        }
        if ((mode & MODE_TRUNCATE) == MODE_TRUNCATE) {
            res |= O_TRUNC;
        }
        if ((mode & MODE_APPEND) == MODE_APPEND) {
            res |= O_APPEND;
        }
        return res;
    }

    @RequiresApi(api = 28)
    static long splice(
            FileDescriptor fdIn, Int64Ref offIn,
            FileDescriptor fdOut, Int64Ref offOut,
            long len, int flags) throws ErrnoException {
        try {
            if (splice == null) {
                splice = Os.class.getMethod("splice",
                        FileDescriptor.class, Int64Ref.class,
                        FileDescriptor.class, Int64Ref.class,
                        long.class, int.class);
            }
            return (long) splice.invoke(null, fdIn, offIn, fdOut, offOut, len, flags);
        } catch (InvocationTargetException e) {
            throw (ErrnoException) e.getTargetException();
        } catch (ReflectiveOperationException e) {
            throw new ErrnoException("splice", ENOSYS);
        }
    }

    @SuppressWarnings("deprecation")
    static long sendfile(
            FileDescriptor outFd, FileDescriptor inFd,
            MutableLong inOffset, long byteCount) throws ErrnoException {
        if (Build.VERSION.SDK_INT >= 28) {
            Int64Ref off = inOffset == null ? null : new Int64Ref(inOffset.value);
            long result = Os.sendfile(outFd, inFd, off, byteCount);
            if (off != null)
                inOffset.value = off.value;
            return result;
        } else {
            try {
                if (os == null) {
                    os = Class.forName("libcore.io.Libcore").getField("os").get(null);
                }
                if (sendfile == null) {
                    sendfile = os.getClass().getMethod("sendfile",
                            FileDescriptor.class, FileDescriptor.class,
                            MutableLong.class, long.class);
                }
                return (long) sendfile.invoke(os, outFd, inFd, inOffset, byteCount);
            } catch (InvocationTargetException e) {
                throw (ErrnoException) e.getTargetException();
            } catch (ReflectiveOperationException e) {
                throw new ErrnoException("sendfile", ENOSYS);
            }
        }
    }
}
