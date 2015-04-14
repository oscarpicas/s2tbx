package org.esa.s2tbx.dataio.deimos;

import org.esa.s2tbx.dataio.deimos.dimap.DeimosConstants;
import org.esa.s2tbx.dataio.readers.BaseProductReaderPlugIn;
import org.esa.snap.framework.dataio.ProductReader;

import java.util.Locale;

/**
 * Visat plugin for reading DEIMOS-1 files.
 * The files are GeoTIFF with DIMAP metadata.
 *
 * @author  Cosmin Cara
 */
public class DeimosProductReaderPlugin extends BaseProductReaderPlugIn {

    @Override
    public Class[] getInputTypes() {
        return DeimosConstants.DIMAP_READER_INPUT_TYPES;
    }

    @Override
    public ProductReader createReaderInstance() {
        return new DeimosProductReader(this);
    }

    @Override
    public String[] getFormatNames() {
        return DeimosConstants.DIMAP_FORMAT_NAMES;
    }

    @Override
    public String[] getDefaultFileExtensions() {
        return DeimosConstants.DIMAP_DEFAULT_EXTENSIONS;
    }

    @Override
    public String getDescription(Locale locale) {
        return DeimosConstants.DIMAP_DESCRIPTION;
    }

    /*@Override
    protected String[] getProductFilePatterns() { return DeimosConstants.FILENAME_PATTERNS; }*/

    @Override
    protected String[] getMinimalPatternList() { return DeimosConstants.MINIMAL_PRODUCT_PATTERNS; }

    @Override
    protected String[] getExclusionPatternList() { return new String[0]; }

}