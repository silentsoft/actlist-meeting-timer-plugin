import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.silentsoft.actlist.plugin.ActlistPlugin;
import org.silentsoft.actlist.plugin.messagebox.MessageBox;

import com.jfoenix.controls.JFXSlider;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Plugin extends ActlistPlugin {
	
	@FXML
	private BorderPane root;
	
	@FXML
	private JFXSlider slider;
	
	private Object monitor;
	private int remainSeconds;
	
	private Thread backgroundThread;
	private List<Stage> transparentStages;

	public static void main(String[] args) throws Exception {
		debug();
	}
	
	public Plugin() throws Exception {
		super("Meeting Timer");
		
		setPluginVersion("1.0.0");
		setPluginAuthor("silentsoft.org", URI.create("https://github.com/silentsoft/actlist-plugin-meeting-timer"));
		setPluginUpdateCheckURI(URI.create("http://actlist.silentsoft.org/api/plugin/54671b40/update/check"));
		
		setMinimumCompatibleVersion(1, 2, 10);
	}

	@Override
	protected void initialize() throws Exception {
		monitor = new Object();
		backgroundThread = null;
		transparentStages = new ArrayList<Stage>();
		slider.valueProperty().addListener((observable, oldValue, newValue) -> {
			int targetMinutes = Math.round(newValue.floatValue());
			synchronized (monitor) {
				remainSeconds = (int) TimeUnit.MINUTES.toSeconds(targetMinutes);
			}
		});
	}

	@Override
	public void pluginActivated() throws Exception {
		int defaultMinutes = 60;
		
		remainSeconds = (int) TimeUnit.MINUTES.toSeconds(defaultMinutes);
		slider.setValue(defaultMinutes);
		
		GraphicsDevice[] graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		if (graphicsDevices != null) {
			for (GraphicsDevice graphicsDevice : graphicsDevices) {
				Stage transparentStage = new Stage();
				transparentStage.setAlwaysOnTop(true);
				transparentStage.initStyle(StageStyle.TRANSPARENT);
				{
					Label label = new Label();
					label.setId("timeLabel");
					label.setTextFill(Color.RED);
					label.setFont(Font.font("Arial", FontWeight.BOLD, 25.0));
					
					BorderPane borderPane = new BorderPane(label);
					borderPane.setBackground(Background.EMPTY);
					
					transparentStage.setScene(new Scene(borderPane, 130, 50, Color.TRANSPARENT));
				}
				transparentStage.setWidth(130);
				transparentStage.setHeight(50);
				{
					Rectangle rectangle = graphicsDevice.getDefaultConfiguration().getBounds();
					
					transparentStage.setX(rectangle.getX() + rectangle.getWidth() - 130);
					transparentStage.setY(rectangle.getY() + rectangle.getHeight() - 70);
				}
				transparentStage.show();
				
				transparentStages.add(transparentStage);
			}
		}
		
		if (backgroundThread == null) {
			backgroundThread = new Thread(() -> {
				while (true) {
					try {
						Platform.runLater(() -> {
							synchronized (monitor) {
								if (transparentStages != null) {
									for (Stage transparentStage : transparentStages) {
										Label timeLabel = (Label) transparentStage.getScene().lookup("#timeLabel");
										if (remainSeconds >= TimeUnit.HOURS.toSeconds(1)) {
											timeLabel.setText(String.format("%02d:%02d:%02d", (int)((remainSeconds/60)/60), ((int)(remainSeconds/60))%60, remainSeconds%60));
										} else {
											timeLabel.setText(String.format("%02d:%02d", ((int)(remainSeconds/60))%60, remainSeconds%60));
										}
										
										if (remainSeconds > 60) {
											timeLabel.setOpacity(0.3);
										} else if (remainSeconds > 0) {
											timeLabel.setOpacity(1.0);
										}
									}
								}
							}
						});
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						synchronized (monitor) {
							if (remainSeconds > 0) {
								remainSeconds--;
								if (remainSeconds % 60 == 0) {
									Platform.runLater(() -> {
										slider.setValue(TimeUnit.SECONDS.toMinutes(remainSeconds));
									});
								}
							} else {
								requestShowActlist();
								
								Platform.runLater(() -> {
									MessageBox.showWarning("TIME TO END THE MEETING");
								});
								
								requestDeactivate();
							}
						}
						
						try {
							Thread.sleep(1000);
						} catch (Exception e) {
							break;
						}
					}
				}
			});
			backgroundThread.start();
		}
	}

	@Override
	public void pluginDeactivated() throws Exception {
		if (backgroundThread != null) {
			backgroundThread.interrupt();
			backgroundThread = null;
		}
		
		for (Stage transparentStage : transparentStages) {
			transparentStage.close();
		}
		
		transparentStages.clear();
	}
	
}
