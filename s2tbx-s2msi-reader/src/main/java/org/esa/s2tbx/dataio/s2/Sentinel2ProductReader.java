package org.esa.s2tbx.dataio.s2;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.esa.s2tbx.dataio.jp2.TileLayout;
import org.esa.s2tbx.dataio.openjpeg.OpenJpegUtils;
import org.esa.s2tbx.dataio.s2.filepatterns.S2ProductFilename;
import org.esa.snap.framework.dataio.AbstractProductReader;
import org.esa.snap.framework.dataio.ProductReaderPlugIn;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.util.SystemUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Base class for all Sentinel-2 product readers
 *
 * @author Nicolas Ducoin
 */
public abstract class Sentinel2ProductReader extends AbstractProductReader {

    public enum ProductInterpretation {
        RESOLUTION_10M,
        RESOLUTION_20M,
        RESOLUTION_60M,
        RESOLUTION_MULTI;
    }

    private S2Config config;
    private File cacheDir;
    private final ProductInterpretation interpretation;


    public Sentinel2ProductReader(ProductReaderPlugIn readerPlugIn, ProductInterpretation interpretation) {
        super(readerPlugIn);
        this.interpretation = interpretation;
        this.config = new S2Config();
    }

    /**
     * @return The configuration file specific to a product reader
     */
    public S2Config getConfig() {
        return config;
    }

    public ProductInterpretation getInterpretation() {
        return interpretation;
    }

    public S2SpatialResolution getProductResolution() {
        switch (interpretation) {
            case RESOLUTION_10M:
                return S2SpatialResolution.R10M;
            case RESOLUTION_20M:
                return S2SpatialResolution.R20M;
            case RESOLUTION_60M:
                return S2SpatialResolution.R60M;
            case RESOLUTION_MULTI:
                return S2SpatialResolution.R10M;
        }
        throw new IllegalStateException("Unknown product interpretation");
    }

    public boolean isMultiResolution() {
        return interpretation == ProductInterpretation.RESOLUTION_MULTI;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    protected abstract Product getMosaicProduct(File granuleMetadataFile) throws IOException;

    protected abstract String getReaderCacheDir();

    protected void initCacheDir(File productDir) throws IOException {
        cacheDir = new File(new File(SystemUtils.getCacheDir(), "s2tbx" + File.separator + getReaderCacheDir()),
                productDir.getName());

        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        if (!cacheDir.exists() || !cacheDir.isDirectory() || !cacheDir.canWrite()) {
            throw new IOException("Can't access package cache directory");
        }
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        SystemUtils.LOG.fine("readProductNodeImpl, " + getInput().toString());

        Product p;

        final File inputFile = new File(getInput().toString());
        if (!inputFile.exists()) {
            throw new FileNotFoundException(inputFile.getPath());
        }

        if (S2ProductFilename.isMetadataFilename(inputFile.getName())) {
            p = getMosaicProduct(inputFile);

            if (p != null) {
                p.setModified(false);
            }
        } else {
            throw new IOException("Unhandled file type.");
        }

        return p;
    }

    /**
     * update the tile layout in S2Config
     *
     * @param metadataFilePath the path to the product metadata file
     * @param isGranule        true if it is the metadata file of a granule
     */
    protected void updateTileLayout(Path metadataFilePath, boolean isGranule) {
        for (S2SpatialResolution layoutResolution : S2SpatialResolution.values()) {
            TileLayout tileLayout;
            if (isGranule) {
                tileLayout = retrieveTileLayoutFromGranuleMetadataFile(
                        metadataFilePath, layoutResolution);
            } else {
                tileLayout = retrieveTileLayoutFromProduct(metadataFilePath, layoutResolution);
            }
            config.updateTileLayout(layoutResolution, tileLayout);
        }
    }


    /**
     * From a granule path, search a jpeg file for the given resolution, extract tile layout
     * information and update
     *
     * @param granuleMetadataFilePath the complete path to the granule metadata file
     * @param resolution              the resolution for which we wan to find the tile layout
     * @return the tile layout for the resolution, or {@code null} if none was found
     */
    public TileLayout retrieveTileLayoutFromGranuleMetadataFile(Path granuleMetadataFilePath, S2SpatialResolution resolution) {
        TileLayout tileLayoutForResolution = null;

        if (Files.exists(granuleMetadataFilePath) && granuleMetadataFilePath.toString().endsWith(".xml")) {
            Path granuleDirPath = granuleMetadataFilePath.getParent();
            tileLayoutForResolution = retrieveTileLayoutFromGranuleDirectory(granuleDirPath, resolution);
        }

        return tileLayoutForResolution;
    }


    /**
     * From a product path, search a jpeg file for the given resolution, extract tile layout
     * information and update
     *
     * @param productMetadataFilePath the complete path to the product metadata file
     * @param resolution              the resolution for which we wan to find the tile layout
     * @return the tile layout for the resolution, or {@code null} if none was found
     */
    public TileLayout retrieveTileLayoutFromProduct(Path productMetadataFilePath, S2SpatialResolution resolution) {
        TileLayout tileLayoutForResolution = null;

        if (Files.exists(productMetadataFilePath) && productMetadataFilePath.toString().endsWith(".xml")) {
            Path productFolder = productMetadataFilePath.getParent();
            Path granulesFolder = productFolder.resolve("GRANULE");
            try {
                DirectoryStream<Path> granulesFolderStream = Files.newDirectoryStream(granulesFolder);

                for (Path granulePath : granulesFolderStream) {
                    tileLayoutForResolution = retrieveTileLayoutFromGranuleDirectory(granulePath, resolution);
                    if (tileLayoutForResolution != null) {
                        break;
                    }
                }
            } catch (IOException e) {
                SystemUtils.LOG.warning("Could not retrieve tile layout for product " +
                        productMetadataFilePath.toAbsolutePath().toString() +
                        " error returned: " +
                        e.getMessage());
            }
        }


        return tileLayoutForResolution;
    }

    /**
     * From a granule path, search a jpeg file for the given resolution, extract tile layout
     * information and update
     *
     * @param granuleMetadataPath the complete path to the granule directory
     * @param resolution          the resolution for which we wan to find the tile layout
     * @return the tile layout for the resolution, or {@code null} if none was found
     */
    private TileLayout retrieveTileLayoutFromGranuleDirectory(Path granuleMetadataPath, S2SpatialResolution resolution) {
        TileLayout tileLayoutForResolution = null;

        Path pathToImages = granuleMetadataPath.resolve("IMG_DATA");

        try {

            for (Path imageFilePath : getImageDirectories(pathToImages, resolution)) {
                try {
                    tileLayoutForResolution =
                            OpenJpegUtils.getTileLayoutWithOpenJPEG(S2Config.OPJ_INFO_EXE, imageFilePath);
                    if (tileLayoutForResolution != null) {
                        break;
                    }
                } catch (IOException | InterruptedException e) {
                    // if we have an exception, we try with the next file (if any)
                    // and log a warning
                    SystemUtils.LOG.warning(
                            "Could not retrieve tile layout for file " +
                                    imageFilePath.toAbsolutePath().toString() +
                                    " error returned: " +
                                    e.getMessage());
                }
            }
        } catch (IOException e) {
            SystemUtils.LOG.warning(
                    "Could not retrieve tile layout for granule " +
                            granuleMetadataPath.toAbsolutePath().toString() +
                            " error returned: " +
                            e.getMessage());
        }

        return tileLayoutForResolution;
    }


    /**
     * For a given resolution, gets the list of band names.
     * For example, for 10m L1C, {"B02", "B03", "B04", "B08"} should be returned
     *
     * @param resolution the resolution for which the band names should be returned
     * @return then band names or {@code null} if not applicable.
     */
    protected abstract String[] getBandNames(S2SpatialResolution resolution);

    /**
     * get an iterator to image files in pathToImages containing files for the given resolution
     * <p>
     * This method is based on band names, if resolution can't be based on band names or if image files are not in
     * pathToImages (like for L2A products), this method has to be overriden
     *
     * @param pathToImages the path to the directory containing the images
     * @param resolution   the resolution for which we want to get images
     * @return a {@link DirectoryStream<Path>}, iterator on the list of image path
     * @throws IOException if an I/O error occurs
     */
    protected DirectoryStream<Path> getImageDirectories(Path pathToImages, S2SpatialResolution resolution) throws IOException {
        return Files.newDirectoryStream(pathToImages, entry -> {
            String[] bandNames = getBandNames(resolution);
            if (bandNames != null) {
                for (String bandName : bandNames) {
                    if (entry.toString().endsWith(bandName + ".jp2")) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    protected Band addBand(Product product, BandInfo bandInfo) {


        TileLayout thisBandTileLayout = bandInfo.getImageLayout();
        TileLayout productTileLayout = getConfig().getTileLayout(getProductResolution());

        float factorX = (float) productTileLayout.width / thisBandTileLayout.width;
        float factorY = (float) productTileLayout.height / thisBandTileLayout.height;

        Band band;
        if (isMultiResolution()) {
            band = new Band(bandInfo.getBandName(), S2Config.SAMPLE_PRODUCT_DATA_TYPE, Math.round(product.getSceneRasterWidth() / factorX), Math.round(product.getSceneRasterHeight() / factorY));
        } else {
            band = new Band(bandInfo.getBandName(), S2Config.SAMPLE_PRODUCT_DATA_TYPE, product.getSceneRasterWidth(), product.getSceneRasterHeight());
        }
        product.addBand(band);

        band.setSpectralBandIndex(bandInfo.getBandIndex());
        band.setSpectralWavelength((float) bandInfo.getSpectralInfo().getWavelengthCentral());
        band.setSpectralBandwidth((float) bandInfo.getSpectralInfo().getSpectralBandwith());

        band.setNoDataValue(0);
        band.setValidPixelExpression(String.format("%s.raw > %s",
                bandInfo.getBandName(), S2Config.RAW_NO_DATA_THRESHOLD));

        return band;
    }

    public static class BandInfo {
        private final Map<String, File> tileIdToFileMap;
        private final int bandIndex;
        private final S2SpectralInformation spectralInformation;
        private final TileLayout imageLayout;

        public BandInfo(Map<String, File> tileIdToFileMap, int bandIndex, S2SpectralInformation spectralInformation, TileLayout imageLayout) {
            this.tileIdToFileMap = Collections.unmodifiableMap(tileIdToFileMap);
            this.bandIndex = bandIndex;
            this.spectralInformation = spectralInformation;
            this.imageLayout = imageLayout;
        }

        public S2SpectralInformation getSpectralInfo() {
            return spectralInformation;
        }

        public Map<String, File> getTileIdToFileMap() {
            return tileIdToFileMap;
        }

        public TileLayout getImageLayout() {
            return imageLayout;
        }

        public int getBandIndex() {
            return bandIndex;
        }

        public String getBandName() {
            return getSpectralInfo().getPhysicalBand();
        }

        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }

    }

}