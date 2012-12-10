package org.esa.beam.dataio.s3.synergy;/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.dataio.s3.AbstractProductFactory;
import org.esa.beam.dataio.s3.Manifest;
import org.esa.beam.dataio.s3.Sentinel3ProductReader;
import org.esa.beam.dataio.s3.SourceImageScaler;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.RasterDataNode;

import javax.media.jai.BorderExtender;
import javax.media.jai.JAI;
import javax.media.jai.operator.CropDescriptor;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.List;

public class VgtProductFactory extends AbstractProductFactory {

    public VgtProductFactory(Sentinel3ProductReader productReader) {
        super(productReader);
    }

    @Override
    protected List<String> getFileNames(Manifest manifest) {
        final List<String> fileNames = new ArrayList<String>();
        fileNames.addAll(manifest.getFileNames("measurementDataSchema"));
        fileNames.addAll(manifest.getFileNames("statusFlagsSchema"));
        fileNames.addAll(manifest.getFileNames("timeCoordinatesSchema"));
        fileNames.addAll(manifest.getFileNames("tiepointsSchema"));
        fileNames.addAll(manifest.getFileNames("geometryDataSchema"));

        return fileNames;
    }

    @Override
    protected void configureTargetNode(Band sourceBand, RasterDataNode targetNode) {
        if (targetNode instanceof Band) {
            final MetadataElement variableAttributes =
                    sourceBand.getProduct().getMetadataRoot().getElement("Variable_Attributes");
            if (variableAttributes != null) {
                final MetadataElement metadataElement = variableAttributes.getElement(sourceBand.getName());
                if (metadataElement != null) {
                    final MetadataAttribute bandwidthAttribute = metadataElement.getAttribute("bandwidth");
                    if (bandwidthAttribute != null) {
                        ((Band) targetNode).setSpectralBandwidth(bandwidthAttribute.getData().getElemFloat());
                    }
                    final MetadataAttribute wavelengthAttribute = metadataElement.getAttribute("wavelength");
                    if (wavelengthAttribute != null) {
                        ((Band) targetNode).setSpectralWavelength(wavelengthAttribute.getData().getElemFloat());
                    }
                }
            }
        }
    }

    @Override
    protected Band addSpecialNode(Band sourceBand, Product targetProduct) {
        final Band targetBand = copyBand(sourceBand, targetProduct, false);
        final RenderingHints renderingHints = new RenderingHints(JAI.KEY_BORDER_EXTENDER,
                                                                 BorderExtender.createInstance(
                                                                         BorderExtender.BORDER_COPY));
        final MultiLevelImage sourceImage = sourceBand.getSourceImage();
        float[] scalings = new float[]{((float)targetBand.getRasterWidth())/sourceBand.getRasterWidth(),
                ((float)targetBand.getRasterHeight())/sourceBand.getRasterHeight()};
        final float transX = (targetBand.getRasterWidth() - sourceImage.getWidth() * scalings[0]) / 2.0f;
        final float transY = (targetBand.getRasterHeight() - sourceImage.getHeight() * scalings[1]) / 2.0f;
        float[] transformations = new float[]{transX, transY};
        // TODO: here we border effects because no-data value is used for interpolation
        final RenderedImage scaledImage = SourceImageScaler.scaleMultiLevelImage(sourceImage, scalings,
                                                                                 transformations, renderingHints);
        final RenderedImage croppedImage = CropDescriptor.create(scaledImage, 0.0f, 0.0f,
                                                                 (float) targetBand.getRasterWidth(),
                                                                 (float) targetBand.getRasterHeight(), null);
        targetBand.setSourceImage(croppedImage);
        return targetBand;
    }

    @Override
    protected void setMasks(Product targetProduct) {
        final ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        for (int i = 0; i < maskGroup.getNodeCount(); i++) {
            final Mask mask = maskGroup.get(i);
            if (mask.getImageType() == Mask.BandMathsType.INSTANCE) {
                final String expression = "SM != 0 && " + Mask.BandMathsType.getExpression(mask);
                Mask.BandMathsType.setExpression(mask, expression);
            }
        }
    }
}
