package setup;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.stage.DirectoryChooser;

public class Controller implements Initializable {

  private static final String quickStart = "https://github.com/Open-RIO/GradleRIO/raw/master/Quickstart.zip";
  /**
   * Size of the buffer to read/write data
   */
  private static final int BUFFER_SIZE = 4096;
  public ChoiceBox<String> ideSelection;
  private Alert setupTimeWait = new Alert(AlertType.INFORMATION,
      "Please wait a minute or 2 for the project to setup. If it has finished setting up you will see Shuffleboard open");
  @FXML
  private TextField projectName;
  @FXML
  private ChoiceBox<String> languageSelection;
  @FXML
  private TextField teamNumber;
  @FXML
  private Button download;
  private boolean textCorrect;
  private boolean teamCorrect = false;

  public static void replaceSelected(String fileLocation, String regexExpression, String replaceWith)
      throws IOException {
    // input the file content to the StringBuffer "input"
    StringBuilder inputBuffer = new StringBuilder(1277);

    try (BufferedReader file = new BufferedReader(new FileReader(fileLocation))) {
      String line;
      while ((line = file.readLine()) != null) {
        inputBuffer.append(line);

        inputBuffer.append(System.lineSeparator());
      }
    }

    String inputStr = inputBuffer.toString();

    Pattern matchPatter = Pattern.compile(regexExpression);
    Matcher matcher = matchPatter.matcher(inputStr);
    inputStr = matcher.replaceAll(replaceWith);

    // write the new String with the replaced line OVER the same file
    try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(
        new FileOutputStream(fileLocation))) {

      bufferedOutputStream.write(inputStr.getBytes());
    }
  }

  private String getGradleVersion() throws IOException {
    String[] command = new String[2];
    command[0] = "powershell.exe";
    command[1] = "gradle -version";
    Process process = Runtime.getRuntime().exec(command);

    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

    String gradle = stdInput.lines().filter(s -> s.startsWith("Gradle")).findFirst().orElse("4.4");

    Pattern compile = Pattern.compile("(\\d+\\.\\d+)");
    Matcher group = compile.matcher(gradle);

    if (group.find()) {
      gradle = group.group(1);
    }

    return gradle;
  }

  @Override
  public final void initialize(URL location, ResourceBundle resources) {
    languageSelection.getItems().addAll("Java", "C++");
    languageSelection.setValue("Java");

    ideSelection.getItems().addAll("Eclipse", "Idea", "CLion");
    ideSelection.setValue("Idea");
  }

  public static String download(String urlR, String projectName) {
//    projectName += ".zip";

    try {
      URL url = new URL(urlR);
      URLConnection conn = url.openConnection();
      try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(projectName)) {
        byte[] b = new byte[1024];
        int count;
        while ((count = in.read(b)) >= 0) {
          out.write(b, 0, count);
        }
      }

    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      projectName = offlineDownload(projectName);
    }

    return projectName;
  }

  private static String offlineDownload(String projectName) {

    ResourceBundle myResources = ResourceBundle
        .getBundle("MyResources", Locale.getDefault(), ClassLoader.getSystemClassLoader());

    String[] split = ((String) myResources.getObject("zipFile")).split(", ");
    byte[] data = new byte[split.length];
    for (int i = 0; i < split.length; i++) {
      data[i] = Byte.parseByte(split[i]);
    }

    try (FileOutputStream fileOutputStream = new FileOutputStream(projectName)) {
      fileOutputStream.write(data);
    } catch (IOException e) {
      e.printStackTrace();
      projectName = null;
    }

    return projectName;
  }

  /**
   * Extracts a zip entry (file entry)
   */
  private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
      byte[] bytesIn = new byte[BUFFER_SIZE];
      int read;
      while ((read = zipIn.read(bytesIn)) != -1) {
        bos.write(bytesIn, 0, read);
      }
    }
  }

  /**
   * Extracts a zip file specified by the zipFilePath to a directory specified by destDirectory (will be created if does
   * not exists)
   */
  private void unzip(String zipFilePath, String destDirectory) {
    try {

      File destDir = new File(destDirectory);
      if (!destDir.exists()) {
        destDir.mkdir();
      }

      unzip(new FileInputStream(zipFilePath), destDirectory);

    } catch (IOException ignored) {

    }
  }

  /**
   * Extracts a zip file specified by the zipFilePath to a directory specified by destDirectory (will be created if does
   * not exists)
   */
  private void unzip(InputStream inputStream, String destDirectory) throws IOException {
    ZipInputStream zipIn = new ZipInputStream(inputStream);
    ZipEntry entry = zipIn.getNextEntry();
    // iterates over entries in the zip file
    while (entry != null) {
      String filePath = destDirectory + File.separator + entry.getName();
      if (entry.isDirectory()) {
        // if the entry is a directory, make the directory
        File dir = new File(filePath);
        dir.mkdir();
      } else {
        // if the entry is a file, extracts it
        extractFile(zipIn, filePath);
      }
      zipIn.closeEntry();
      entry = zipIn.getNextEntry();
    }
    inputStream.close();
    zipIn.close();
  }

  public final void setupProject(ActionEvent actionEvent) throws IOException {

    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose folder to place project into");
    File defaultDirectory = new File(System.getProperty("user.home"));
    chooser.setInitialDirectory(defaultDirectory);
    File selectedDirectory = chooser.showDialog(((Node) actionEvent.getTarget()).getScene().getWindow());

    if (selectedDirectory != null && selectedDirectory.exists()) {

      String realFolder = selectedDirectory + File.separator + projectName.getText();

      String zipFileName = download(quickStart, realFolder);
//      Path path = Paths.get(selectedDirectory.getPath(), "Quickstart.zip");
//      System.out.println(path);
//      Path copy = Files.copy(Paths.get("Quickstart.zip"), path);
//      System.out.println(copy);
//      String zipFileName = path.toAbsolutePath().toString();
//      System.out.println(zipFileName);
      //

      unzip(zipFileName, realFolder); //this was copied

      delete(new File(zipFileName));

//      TODO use enum instead
      String language = languageSelection.getValue().equals("Java") ? "cpp" : "java";
      String deletedLanguageDirectory = realFolder + File.separator + language;
      language = language.equals("java") ? "cpp" : "java";
      String languageDirectory = realFolder + File.separator + language;

      delete(new File(deletedLanguageDirectory));

      File teamNamePath = Paths.get(languageDirectory, "src", "main", "java", "frc", "team0000").toFile();

      String teamNumberString = "team" + teamNumber.getText();
      File newTeamNamePath = Paths.get(languageDirectory, "src", "main", "java", "frc", teamNumberString)
          .toFile();

      teamNamePath.renameTo(newTeamNamePath);

      if (language.equals("java")) {
        String gradleBuildFile = Paths.get(realFolder, language, "build.gradle").toString();

        String gradleVersion = getGradleVersion();
        replaceSelected(gradleBuildFile, "4.4", gradleVersion);

        replaceSelected(gradleBuildFile, "5333", teamNumber.getText());
        replaceSelected(gradleBuildFile, "0000", teamNumber.getText());
        String robotJavaFile = Paths.get(newTeamNamePath.getPath(), "robot", "Robot.java").toString();
        replaceSelected(robotJavaFile, "0000", teamNumber.getText());
      }

      moveFolder(languageDirectory, realFolder);

      setupGradleProject(realFolder, ideSelection.getValue().toLowerCase());
//      setupTimeWait.show();
      openFolderInExplorer(realFolder);
//      ((Node) actionEvent.getTarget()).getScene().getWindow().hide();
    }
  }

  private void moveFolder(String fromDirectory, String toDirectory) throws IOException {
    File filesDirectory = new File(fromDirectory);
    File realDirectory = new File(toDirectory);

    if (realDirectory.isDirectory() && filesDirectory.isDirectory()) {
      File[] content = filesDirectory.listFiles();
      if (content != null) {
        for (File aContent : content) {
          Files.move(aContent.toPath(), Paths.get(realDirectory.toPath().toString(), aContent.getName()));
        }
      }
    }

    delete(filesDirectory);
  }

  private void setupGradleProject(String folderLocation, String ide) throws IOException {

    /*
    Using the cmd.
    String[] command = new String[3];
    command[0] = "cmd";
    command[1] = "/c";
    command[2] = String.format("cd %s && gradlew %s && gradlew build && gradlew shuffleboard", folderLocation, ide);
    */

    String[] command = new String[2];
    command[0] = "powershell.exe";
    command[1] = String
        .format(
            "Get-Location; Set-Location -Path %s; Get-Location; gradle wrapper; .\\gradlew %s; .\\gradlew build; .\\gradlew shuffleboard",
            folderLocation, ide);

    Process proc = Runtime.getRuntime().exec(command);

    CmdOutputDisplay.show(proc);
  }

  private void openFolderInExplorer(String fileLocation) throws IOException {
    Runtime.getRuntime().exec(String.format("explorer.exe /select,%s\\src", fileLocation));
  }

  private void delete(File f) throws IOException {
    if (f.isDirectory()) {
      for (File c : Objects.requireNonNull(f.listFiles())) {
        delete(c);
      }
    }
    if (!f.delete()) {
      throw new FileNotFoundException("Failed to delete file: " + f);
    }
  }

  public final void checkText(KeyEvent keyEvent) {
//		if (((TextField) keyEvent.getTarget()).getText().length() == 0 && Character
//			.isDigit(keyEvent.getCharacter().charAt(0))) {
//			keyEvent.consume();
//			return;
//		}
    if (keyEvent.getCharacter().matches("[0-9\\s]")) {
      keyEvent.consume();
    } else if ((((TextInputControl) keyEvent.getTarget()).getText().length() <= 0) && keyEvent.getCharacter()
        .equals("\b")) {
      textCorrect = false;
      download.setDisable(true);
    } else {
      textCorrect = true;
      download.setDisable(!teamCorrect);
    }
  }

  public final void checkNumber(KeyEvent keyEvent) {
    if ((((TextInputControl) keyEvent.getTarget()).getText().length() <= 0) && keyEvent.getCharacter()
        .equals("\b")) {
      teamCorrect = false;
      download.setDisable(true);
      return;
    }

    if (Character.isDigit(keyEvent.getCharacter().charAt(0))) {
      teamCorrect = true;
      download.setDisable(!textCorrect);
    } else if (!keyEvent.getCharacter().equals("\b")) {
      keyEvent.consume();
    }
  }
}
