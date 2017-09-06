package view;

import com.lynden.gmapsfx.GoogleMapView;
import com.lynden.gmapsfx.MapComponentInitializedListener;
import com.lynden.gmapsfx.javascript.object.GoogleMap;
import com.lynden.gmapsfx.javascript.object.LatLong;
import com.lynden.gmapsfx.javascript.object.MapOptions;
import com.lynden.gmapsfx.service.geocoding.GeocoderStatus;
import com.lynden.gmapsfx.service.geocoding.GeocodingResult;
import com.lynden.gmapsfx.service.geocoding.GeocodingService;
import controller.THOrganizer$;
import domain.TreasureHunt;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import utils.Strings;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Google Maps controller. It allows to render the Google Maps View specified in .fxml file.
 */
public class GoogleMapsFXMLController implements Initializable, MapComponentInitializedListener {

    private final static int DEFAULT_ZOOM = 15;
    private final static double DEFAULT_LAT = 47.6097;
    private final static double DEFAULT_LONG = -122.3331;

    @FXML
    private GoogleMapView mapView;

    @FXML
    private TextField addressTextField;

    @FXML
    private Button confirmButton;

    private GoogleMap map;

    private GeocodingService geocodingService;

    private final StringProperty address = new SimpleStringProperty();

    private final TreasureHunt currentTreasureHunt;

    public GoogleMapsFXMLController(final TreasureHunt th) {
        this.currentTreasureHunt = th;
    }

    @Override
    public void mapInitialized() {
        this.geocodingService = new GeocodingService();
        MapOptions mapOptions = new MapOptions();
        mapOptions.overviewMapControl(false)
                .panControl(false)
                .rotateControl(false)
                .scaleControl(false)
                .streetViewControl(false)
                .zoom(DEFAULT_ZOOM)
                .center(new LatLong(DEFAULT_LAT, DEFAULT_LONG));
        this.map = this.mapView.createMap(mapOptions);
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        this.mapView.addMapInializedListener(this);
        this.address.bind(this.addressTextField.textProperty());
        this.addressTextField.setOnAction(event -> this.geocodingService.geocode(this.address.get(), (GeocodingResult[] results, GeocoderStatus status) -> {
            LatLong latLong;
            if (status == GeocoderStatus.ZERO_RESULTS) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "No matching address found");
                alert.show();
                return;
            } else if (results.length > 1) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Multiple results found, showing the first one.");
                alert.show();
                latLong = new LatLong(results[0].getGeometry().getLocation().getLatitude(), results[0].getGeometry().getLocation().getLongitude());
            } else {
                latLong = new LatLong(results[0].getGeometry().getLocation().getLatitude(), results[0].getGeometry().getLocation().getLongitude());
            }
            this.map.setCenter(latLong);
            this.currentTreasureHunt.location_$eq(this.addressTextField.getText());
        }));

        this.confirmButton.setText(Strings.CONFIRM_TH_BUTTON);
        this.confirmButton.setOnAction(event -> {
            this.confirmButton.setDisable(true);
            this.addressTextField.setDisable(true);
            THOrganizer$.MODULE$.instance().createTreasureHunt(this.currentTreasureHunt);
        });
    }

}
