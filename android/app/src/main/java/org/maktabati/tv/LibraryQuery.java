package org.maktabati.tv;
import java.util.Locale;
/** Pure local matching: no filename/URI or query is sent to a service. */
final class LibraryQuery {
    private static final java.util.regex.Pattern SPACE=java.util.regex.Pattern.compile("\\s+");
    static String normalize(String value){
        StringBuilder out=new StringBuilder();
        for(char c:(value==null?"":value).toCharArray()){
            if(c>='٠'&&c<='٩')c=(char)('0'+c-'٠');
            else if(c>='۰'&&c<='۹')c=(char)('0'+c-'۰');
            if(c>=0x064b&&c<=0x065f)continue;
            out.append(c);
        }
        return SPACE.matcher(out.toString().toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }
    static boolean matches(String title,Integer year,String query,int selectedYear){
        if(selectedYear>0&&(year==null||year!=selectedYear))return false;
        String hay=normalize(title)+" "+(year==null?"":year);
        String q=normalize(query);if(q.isEmpty())return true;
        for(String term:q.split(" "))if(!hay.contains(term))return false;
        return true;
    }
    static int compare(String left,Integer leftYear,String right,Integer rightYear,int sort){
        if(sort>=2){
            if(leftYear==null&&rightYear!=null)return 1;
            if(rightYear==null&&leftYear!=null)return -1;
            if(leftYear!=null&&rightYear!=null){int n=Integer.compare(leftYear,rightYear);if(n!=0)return sort==2?-n:n;}
        }
        int n=normalize(left).compareTo(normalize(right));return sort==1?-n:n;
    }
}
