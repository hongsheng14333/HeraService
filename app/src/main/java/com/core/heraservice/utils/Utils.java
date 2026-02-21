package com.core.heraservice.utils;

import android.util.Log;

import java.io.File;
public class Utils {
    public static final String TAG = "Utils";

    public static int createDir(String dirPath) {
        File dir = new File(dirPath);
        //文件夹是否已经存在
        if (dir.exists()) {
            Log.w(TAG,"The directory [ " + dirPath + " ] has already exists");
            return 1;
        }

        if (!dirPath.endsWith(File.separator)) {//不是以 路径分隔符 "/" 结束，则添加路径分隔符 "/"
            dirPath = dirPath + File.separator;
        }

        //创建文件夹
        if (dir.mkdirs()) {
            Log.d(TAG,"create directory [ "+ dirPath + " ] success");
            return 0;
        }

        Log.e(TAG,"create directory [ "+ dirPath + " ] failed");
        return -1;
    }

    public static String getMsgWithErrorCode(int code, String status) {
        String str = "错误码:" + code;
        switch (code) {
            case 140001:
                str += " 错误信息: 引擎未创建, 请检查是否成功初始化, 详情可查看运行日志.";
                break;
            case 140008:
                str += " 错误信息: 鉴权失败, 请关注日志中详细失败原因.";
                break;
            case 140011:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause接口.";
                break;
            case 140013:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause/release等接口.";
                break;
            case 140900:
                str += " 错误信息: tts引擎初始化失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140901:
                str += " 错误信息: tts引擎初始化失败, 请检查使用的SDK是否支持离线语音合成功能.";
                break;
            case 140903:
                str += " 错误信息: tts引擎任务创建失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140908:
                str += " 错误信息: 发音人资源无法获得正确采样率, 请检查发音人资源是否正确.";
                break;
            case 140910:
                str += " 错误信息: 发音人资源路径无效, 请检查发音人资源文件路径是否正确.";
                break;
            case 144002:
                str += " 错误信息: 若发生于语音合成, 可能为传入文本超过16KB. 可升级到最新版本, 具体查看日志确认.";
                break;
            case 144003:
                str += " 错误信息: token过期或无效, 请检查token是否有效.";
                break;
            case 144004:
                str += " 错误信息: 语音合成超时, 具体查看日志确认.";
                break;
            case 144006:
                str += " 错误信息: 云端返回未分类错误, 请看详细的错误信息.";
                break;
            case 144103:
                str += " 错误信息: 设置参数无效, 请参考接口文档检查参数是否正确, 也可通过task_id咨询客服.";
                break;
            case 144505:
                str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置参数及服务地址.";
                break;
            case 170008:
                str += " 错误信息: 鉴权成功, 但是存储鉴权信息的文件路径不存在或无权限.";
                break;
            case 170806:
                str += " 错误信息: 请设置SecurityToken.";
                break;
            case 170807:
                str += " 错误信息: SecurityToken过期或无效, 请检查SecurityToken是否有效.";
                break;
            case 240002:
                str += " 错误信息: 设置的参数不正确, 比如设置json参数格式不对, 设置的文件无效等.";
                break;
            case 240005:
                if (status == "init") {
                    str += " 错误信息: 请检查appkey、akId、akSecret、url等初始化参数是否无效或空.";
                } else {
                    str += " 错误信息: 传入参数无效, 请检查参数正确性.";
                }
                break;
            case 240008:
                str += " 错误信息: SDK内部核心引擎未成功初始化.";
                break;
            case 240011:
                str += " 错误信息: SDK未成功初始化.";
                break;
            case 240040:
                str += " 错误信息: 本地引擎初始化失败，可能是资源文件(如kws.bin)损坏.";
                break;
            case 240052:
                str += " 错误信息: 2s未传入音频数据，请检查录音相关代码、权限或录音模块是否被其他应用占用.";
                break;
            case 240063:
                str += " 错误信息: SSL错误，可能为SSL建连失败。比如token无效或者过期，或SSL证书校验失败(可升级到最新版)等等，具体查日志确认.";
                break;
            case 240068:
                str += " 错误信息: 403 Forbidden, token无效或者过期.";
                break;
            case 240070:
                str += " 错误信息: 鉴权失败, 请查看日志确定具体问题, 特别是关注日志 E/iDST::ErrMgr: errcode=.";
                break;
            case 240072:
                str += " 错误信息: 录音文件识别传入的录音文件不存在.";
                break;
            case 240073:
                str += " 错误信息: 录音文件识别传入的参数错误, 比如audio_address不存在或file_path不存在或其他参数错误.";
                break;
            case 240075:
                str += " 错误信息: 录音文件识别失败，比如账号无权限等.";
                break;
            case 10000016:
                if (status.contains("403 Forbidden")) {
                    str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置的账号临时凭证.";
                } else if (status.contains("404 Forbidden")) {
                    str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置的服务地址URL.";
                } else {
                    str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置的参数及服务地址.";
                }
                break;
            case 40000004:
                str += " 错误信息: 长时间未收到指令或音频.";
                break;
            case 40000010:
                str += " 错误信息: 此账号试用期已过, 请开通商用版或检查账号权限.";
                break;
            case 41010105:
                str += " 错误信息: 长时间未收到人声，触发静音超时.";
                break;
            case 999999:
                str += " 错误信息: 库加载失败, 可能是库不支持当前activity, 或库加载时崩溃, 可详细查看日志判断.";
                break;
            default:
                str += " 未知错误信息, 请查看官网错误码和运行日志确认问题.";
        }
        return str;
    }
}
