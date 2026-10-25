package org.maktabati.tv;
import org.junit.*;import org.junit.rules.TemporaryFolder;import java.io.*;import java.nio.file.*;
public class MovieDeletionTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 File write(File folder,String name)throws Exception{File f=new File(folder,name);Files.write(f.toPath(),new byte[]{1,2,3});return f;}
 @Test public void deletesOnlyConfirmedDedicatedFolder()throws Exception{File root=temp.newFolder(),folder=new File(root,"movie");folder.mkdir();File video=write(folder,"movie.mkv"),other=write(root,"keep.txt");write(folder,"movie.srt");MovieDeletion.Plan plan=MovieDeletion.preview(root,video);Assert.assertEquals(2,plan.count);MovieDeletion.execute(plan);Assert.assertFalse(folder.exists());Assert.assertTrue(other.exists());}
 @Test(expected=IOException.class) public void refusesRoot()throws Exception{File root=temp.newFolder();MovieDeletion.preview(root,write(root,"movie.mkv"));}
 @Test(expected=IOException.class) public void refusesMultipleVideos()throws Exception{File root=temp.newFolder(),folder=new File(root,"movies");folder.mkdir();File video=write(folder,"one.mkv");write(folder,"two.mp4");MovieDeletion.preview(root,video);}
 @Test public void changedFolderLeavesEverythingUntouched()throws Exception{File root=temp.newFolder(),folder=new File(root,"movie");folder.mkdir();File video=write(folder,"one.mkv");MovieDeletion.Plan p=MovieDeletion.preview(root,video);File added=write(folder,"new.txt");try{MovieDeletion.execute(p);Assert.fail();}catch(IOException expected){}Assert.assertTrue(video.exists());Assert.assertTrue(added.exists());}
 @Test(expected=IOException.class) public void refusesOutsideVolume()throws Exception{File root=temp.newFolder(),outside=temp.newFolder();MovieDeletion.preview(root,write(outside,"one.mkv"));}
}
