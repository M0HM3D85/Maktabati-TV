package org.maktabati.tv;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Never expose exception messages: network errors may contain URLs or private headers. */
final class MetadataFailure {
    static final class Http extends IOException {
        final int status;Http(int s){super("Metadata HTTP failure");status=s;}
    }
    static final class Credential extends IOException {
        final String reason;Credential(String r){super("Credential unavailable");reason=r;}
    }
    static String code(Throwable e){
        for(int n=0;e!=null&&n<8;n++,e=e.getCause()){
            if(e instanceof Credential)return ((Credential)e).reason;
            if(e instanceof Http){int s=((Http)e).status;return s==401||s==403?"AUTH":s==429?"RATE_LIMIT":"HTTP";}
            if(e instanceof SSLException)return "TLS";
            if(e instanceof UnknownHostException)return "DNS";
            if(e instanceof SocketTimeoutException)return "TIMEOUT";
            if(e instanceof java.security.GeneralSecurityException)return "DECRYPT";
            if(e instanceof org.json.JSONException)return "JSON";
        }
        return "NETWORK";
    }
    static String message(Throwable e){
        String code=code(e),text;
        switch(code){
            case "MISSING":text="لا يوجد رمز قراءة محفوظ. اربط الخدمة من الإعدادات.";break;
            case "FORMAT":text="صيغة رمز القراءة غير مكتملة. الصق API Read Access Token كاملًا على التلفزيون.";break;
            case "DECRYPT":text="تعذر فك تشفير رمز الربط المحفوظ. أعد إدخاله من الإعدادات؛ لم يُحذف الرمز الحالي.";break;
            case "AUTH":text="رفضت TMDb رمز القراءة. راجع صلاحية الرمز في حسابك.";break;
            case "RATE_LIMIT":text="طلبات كثيرة. انتظر قليلًا ثم أعد المحاولة.";break;
            case "TLS":text="تعذر إنشاء اتصال HTTPS آمن. تحقق من تاريخ التلفزيون واتصاله.";break;
            case "DNS":text="تعذر الوصول إلى اسم خادم TMDb. تحقق من الشبكة.";break;
            case "TIMEOUT":text="انتهت مهلة الاتصال. حاول مرة أخرى.";break;
            case "JSON":text="وصل رد غير متوقع من خدمة المعلومات.";break;
            case "HTTP":text="خدمة المعلومات غير متاحة حاليًا.";break;
            default:text="تعذر الاتصال بخدمة المعلومات. تحقق من الشبكة.";
        }
        return text+" ["+code+"]";
    }
    static String normalizeToken(String raw)throws Credential{
        String value=raw==null?"":raw.trim();
        if(value.startsWith("Bearer "))value=value.substring(7).trim();
        if(value.isEmpty())throw new Credential("MISSING");
        if(value.length()<30||value.length()>3000||!value.matches("[A-Za-z0-9._~+/-]+={0,2}"))throw new Credential("FORMAT");
        return value;
    }
}
