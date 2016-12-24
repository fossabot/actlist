package org.silentsoft.actlist.plugin;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

import org.controlsfx.control.PopOver;
import org.silentsoft.actlist.BizConst;
import org.silentsoft.actlist.application.App;
import org.silentsoft.actlist.plugin.ActlistPlugin.Function;
import org.silentsoft.core.util.FileUtil;
import org.silentsoft.core.util.JSONUtil;
import org.silentsoft.io.event.EventHandler;
import org.silentsoft.io.event.EventListener;
import org.silentsoft.io.memory.SharedMemory;

import com.jfoenix.controls.JFXHamburger;
import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.controls.JFXToggleButton;


public class PluginComponent implements EventListener {

	@FXML
	private AnchorPane root;
	
	@FXML
	private JFXHamburger hand;
	
	@FXML
	private HBox pluginLoadingBox;
	
	@FXML
	private Label lblPluginName;
	
	@FXML
	private JFXToggleButton togActivator;
	
	@FXML
	private VBox contentLoadingBox;
	
	@FXML
	private VBox contentBox;
	
	private String pluginFileName;
	
	private ActlistPlugin plugin;
	
	private PopOver popOver;
	
	public void initialize() {
		// This method is automatically called by FXMLLoader.
	}
	
	public void initialize(Class<? extends ActlistPlugin> pluginClass, String pluginFileName, boolean activated) {
		new Thread(() -> {
			try {
				plugin = pluginClass.newInstance();
				
				plugin.setPluginConfig(new PluginConfig(pluginFileName));
				File configFile = Paths.get(System.getProperty("user.dir"), "plugins", "config", pluginFileName.concat(".config")).toFile();
				if (configFile.exists()) {
					String configContent = FileUtil.readFile(configFile);
					PluginConfig pluginConfig = JSONUtil.JSONToObject(configContent, PluginConfig.class);
					if (pluginConfig != null) {
						plugin.setPluginConfig(pluginConfig);
					}
				}
				
				plugin.initialize();
				
				this.pluginFileName = pluginFileName;
				String pluginName = plugin.getPluginName();
				String pluginDescription = plugin.getPluginDescription();
				
				Platform.runLater(() -> {
					makeDraggable();

					lblPluginName.setText(pluginName);
					if (pluginDescription != null && "".equals(pluginDescription) == false) {
						lblPluginName.setTooltip(new Tooltip(pluginDescription));
					}
					
					togActivator.setSelected(activated);
					
					popOver = new PopOver(new VBox());
					((VBox) popOver.getContentNode()).setPadding(new Insets(3, 3, 3, 3));
					popOver.setArrowLocation(PopOver.ArrowLocation.TOP_LEFT);
					
					for (Function function : plugin.getFunctionMap().values()) {
						Label label = new Label("", function.graphic);
						label.setOnMouseClicked(mouseEvent -> {
							try {
								if (function.action != null) {
									function.action.run();
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						});
						
						addNodeToPopOver(label);
					}
					
					if (plugin.getFunctionMap().size() > 0) {
						((VBox) popOver.getContentNode()).getChildren().add(new Separator(Orientation.HORIZONTAL));
					}
					
					Label label = new Label("About");
					label.setOnMouseClicked(mouseEvent -> {
						Alert alert = new Alert(AlertType.INFORMATION);
						((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().addAll(App.getIcons());
						alert.initOwner(App.getStage());
						alert.setTitle("About");
						alert.setHeaderText(plugin.getPluginName());
						if (plugin.getPluginDescription() != null) {
							StringBuffer contentText = new StringBuffer();
							contentText.append(plugin.getPluginDescription());
							if (plugin.getPluginAuthor() != null) {
								contentText.append("\r\n\r\n");
								contentText.append(String.join("", "by ", plugin.getPluginAuthor()));
							}
							alert.setContentText(contentText.toString());
						}
						
						alert.showAndWait();
					});
					addNodeToPopOver(label);
					
					if (activated) {
						activated();
					}
					
					plugin.shouldShowLoadingBar().addListener((observable, oldValue, newValue) -> {
						if (oldValue == newValue) {
							return;
						}
						
						displayLoadingBar(newValue);
					});
					
					EventHandler.addListener(this);
				});
			} catch (Exception e) {
				e.printStackTrace();
				Platform.runLater(() -> {
					lblPluginName.setText(pluginFileName);
					togActivator.setUnToggleLineColor(Paint.valueOf("#da4242"));
					togActivator.setDisable(true);
					togActivator.setOpacity(1.0); // remove disable effect.
					
					EventHandler.removeListener(this);
				});
			} finally {
				pluginLoadingBox.setVisible(false);
			}
		}).start();
	}
	
	private void makeDraggable() {
		hand.setOnDragDetected(mouseEvent -> {
			createSnapshot(mouseEvent);
			
			root.startFullDrag();
		});
		root.addEventFilter(MouseDragEvent.MOUSE_DRAG_ENTERED, mouseDragEvent -> {
			root.setStyle("-fx-background-color: #f2f2f2;");
			
			mouseDragEvent.consume();
		});
		root.addEventFilter(MouseDragEvent.MOUSE_DRAG_RELEASED, mouseDragEvent -> {
			deleteSnapshot();
			
			// move index of dragging node to index of drop target.
			VBox componentBox = (VBox) SharedMemory.getDataMap().get(BizConst.KEY_COMPONENT_BOX);
			int indexOfDraggingNode = componentBox.getChildren().indexOf(mouseDragEvent.getGestureSource());
			int indexOfDropTarget = componentBox.getChildren().indexOf(root);
			if (indexOfDraggingNode >= 0 && indexOfDropTarget >= 0) {
				final Node node = componentBox.getChildren().remove(indexOfDraggingNode);
				componentBox.getChildren().add(indexOfDropTarget, node);
			}
			
			mouseDragEvent.consume();
		});
		root.addEventFilter(MouseDragEvent.MOUSE_DRAG_EXITED, mouseDragEvent -> {
			root.setStyle("-fx-background-color: #ffffff;");
			
			mouseDragEvent.consume();
		});
		hand.setOnMouseReleased(mouseEvent -> {
			// in most cases, the 'root' will consume the mouse drag event. so will not being called this event.
			// but the meaning of this event being called is that it is outside the drag area. so, must remove the snapshot.
			deleteSnapshot();
		});
	}
	
	private void createSnapshot(MouseEvent mouseEvent) {
		ImageView snapshot = new ImageView(root.snapshot(null, null));
		snapshot.setManaged(false);
		snapshot.setMouseTransparent(true);
		snapshot.setEffect(new DropShadow(3.0, 0.0, 1.5, Color.valueOf("#333333")));
		
		VBox componentBox = (VBox) SharedMemory.getDataMap().get(BizConst.KEY_COMPONENT_BOX);
		componentBox.getChildren().add(snapshot);
		componentBox.setUserData(snapshot);
		componentBox.setOnMouseDragged(event -> {
			snapshot.relocate(event.getX() - mouseEvent.getX(), event.getY() - mouseEvent.getY());
		});
	}
	
	private void deleteSnapshot() {
		VBox componentBox = (VBox) SharedMemory.getDataMap().get(BizConst.KEY_COMPONENT_BOX);
		componentBox.setOnMouseDragged(null);
		componentBox.getChildren().remove(componentBox.getUserData());
		componentBox.setUserData(null);
	}
	
	private void addNodeToPopOver(Node node) {
		HBox hBox = new HBox(node);
		hBox.setAlignment(Pos.CENTER);
		hBox.setPadding(new Insets(3, 3, 3, 3));
		hBox.setStyle("-fx-background-color: white;");
		hBox.setOnMouseEntered(mouseEvent -> {
			hBox.setStyle("-fx-background-color: lightgray;");
		});
		hBox.setOnMouseExited(mouseEvent -> {
			hBox.setStyle("-fx-background-color: white;");
		});
		((VBox) popOver.getContentNode()).getChildren().add(hBox);
	}
	
	private void loadPluginGraphic() {
		/*
		  <VBox fx:id="contentBox" layoutX="35.0" layoutY="50.0" prefWidth="380.0" AnchorPane.leftAnchor="35.0" AnchorPane.rightAnchor="20.0">
		     <children>
		        <!-- Generate by code. 
		        <BorderPane fx:id="contentPane" />
		        <Separator prefWidth="215.0">
		           <padding>
		              <Insets top="5.0" />
		           </padding>
		        </Separator>
		        -->
		     </children>
		  </VBox>
		  <VBox fx:id="contentLoadingBox" visible="false" alignment="CENTER" layoutX="35.0" layoutY="50.0" prefWidth="380.0" style="-fx-background-color: white;" AnchorPane.leftAnchor="35.0" AnchorPane.rightAnchor="0.0">
		     <children>
		        <!-- Generate by code. 
		        <JFXSpinner />
		        -->
		     </children>
		  </VBox>
		 */
		
		try {
			if (plugin.existsGraphic()) {
				Node pluginContent = plugin.getGraphic();
				if (pluginContent != null) {
					contentBox.getChildren().add(new BorderPane(pluginContent));
					Separator contentLine = new Separator();
					contentLine.setPrefWidth(215.0);
					contentLine.setPadding(new Insets(5.0, 0.0, 0.0, 0.0));
					contentBox.getChildren().add(contentLine);
				}
			}
		} catch (Exception e) {
			
		}
	}
	
	private void displayLoadingBar(boolean shouldShowLoadingBar) {
		if (plugin.existsGraphic()) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					contentLoadingBox.getChildren().clear();
					
					if (shouldShowLoadingBar) {
						contentLoadingBox.getChildren().add(new JFXSpinner());
					}
					
					contentLoadingBox.setVisible(shouldShowLoadingBar);
				}
			};
			
			if (Platform.isFxApplicationThread()) {
				runnable.run();
			} else {
				Platform.runLater(() -> {
					runnable.run();
				});
			}
		}
	}
	
	@FXML
	private void mouseClicked(MouseEvent e) {
		if (e.getButton() == MouseButton.SECONDARY) {
			if (togActivator.selectedProperty().get()) {
				// reason of why the owner is pluginLoadingBox is for hiding automatically when lost focus.
				popOver.show(pluginLoadingBox, e.getScreenX(), e.getScreenY());
			}
		}
	}
	
	@FXML
	private void toggleOnAction() {
		if (togActivator.selectedProperty().get()) {
			activated();
		} else {
			deactivated();
		}
	}
	
	private void activated() {
		displayLoadingBar(true);
		
		new Thread(() -> {
			Platform.runLater(() -> {
				try {
					plugin.pluginActivated();
					loadPluginGraphic();
					
					displayLoadingBar(false);
					
					List<String> deactivatedPlugins = (List<String>) SharedMemory.getDataMap().get(BizConst.KEY_DEACTIVATED_PLUGINS);
					deactivatedPlugins.remove(pluginFileName);
					EventHandler.callEvent(getClass(), BizConst.EVENT_SAVE_DEACTIVATED_PLUGINS);
				} catch (Exception e) {
					
				}
			});
		}).start();
	}
	
	private void deactivated() {
		new Thread(() -> {
			Platform.runLater(() -> {
				try {
					List<String> deactivatedPlugins = (List<String>) SharedMemory.getDataMap().get(BizConst.KEY_DEACTIVATED_PLUGINS);
					deactivatedPlugins.add(pluginFileName);
					EventHandler.callEvent(getClass(), BizConst.EVENT_SAVE_DEACTIVATED_PLUGINS);
					
					contentBox.getChildren().clear();
					contentLoadingBox.getChildren().clear();
					popOver.hide();
					plugin.pluginDeactivated();
				} catch (Exception e) {
					
				}
			});
		}).start();
	}

	@Override
	public void onEvent(String event) {
		if (togActivator.selectedProperty().get()) {
			try {
				switch (event) {
				case BizConst.EVENT_APPLICATION_ACTIVATED:
					plugin.applicationActivated();
					break;
				case BizConst.EVENT_APPLICATION_DEACTIVATED:
					plugin.applicationDeactivated();
					break;
				}
			} catch (Exception e) {
				
			}
		}
	}
}
