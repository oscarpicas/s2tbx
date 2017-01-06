package org.esa.s2tbx.dataio.imagewriter;

import javax.xml.stream.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;


/**
 * Class that prints the JP2 XML header
 * Created by rdumitrascu on 11/15/2016.
 */
public class XMLBoxWriter {
    private FileOutputStream fileOutputStream;
    private JP2MetadataResources jp2MetadataResources;
    private final static Logger logger = Logger.getLogger(XMLBoxWriter.class.getName());

    /**
     * XMLBoxWriter constructor
     */
    public XMLBoxWriter(){
        this.fileOutputStream = null;
        this.jp2MetadataResources = null;

    }


    public void setResources(FileOutputStream fileOutputStream, JP2MetadataResources jp2Metadata) throws XMLStreamException, IOException {
        if(fileOutputStream == null){
            logger.warning("no fileOutputStream has been set");
            throw new IllegalArgumentException();
        }
        if(jp2Metadata == null){
            logger.warning("no jp2MetadataRespurces has been received");
            throw new IllegalArgumentException();
        }

        this.fileOutputStream = fileOutputStream;
        this.jp2MetadataResources = jp2Metadata;

        xmlJP2WriteStream(fileOutputStream);

    }

    /**
     *
     * @param fileOutputStream
     * @throws XMLStreamException
     */
    private void xmlJP2WriteStream(FileOutputStream fileOutputStream ) throws XMLStreamException, IOException {

        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter tmpWriter = outputFactory.createXMLStreamWriter(this.fileOutputStream);
        writeStartDocument(tmpWriter);
        tmpWriter.flush();
        tmpWriter.close();
    }

    /**
     *
     * @param tmpWriter
     * @throws XMLStreamException
     */
    private void writeStartDocument(XMLStreamWriter tmpWriter) throws XMLStreamException, IOException {
        tmpWriter.writeStartDocument("UTF-8", "1.0");
        tmpWriter.writeCharacters("\n");
        tmpWriter.writeStartElement("gml:FeatureCollection");
        tmpWriter.writeNamespace("gml","http://www.opengis.net/gml");
        tmpWriter.writeNamespace("xsi","http://www.w3.org/2001/XMLSchema-instance");
        tmpWriter.writeAttribute("http://www.w3.org/2001/XMLSchema-instance","schemaLocation","http://www.opengeospatial.net/gml http://schemas.opengis.net/gml/3.1.1/profiles/gmlJP2Profile/1.0.0/gmlJP2Profile.xsd");
        writeFutureMemberTags(tmpWriter);
        tmpWriter.writeEndDocument();
        tmpWriter.writeCharacters(" ");
        this.fileOutputStream.write(0x00);
    }

    /**
     *
     * @param tmpWriter
     * @throws XMLStreamException
     */
    private void writeFutureMemberTags(XMLStreamWriter tmpWriter) throws XMLStreamException {
        tmpWriter.writeStartElement("gml:featureMember");
        tmpWriter.writeStartElement("gml:FeatureCollection");
        tmpWriter.writeStartElement("gml:featureMember");
        writeRectifiedGrid(tmpWriter);
        writeEndElement(3,tmpWriter);
    }

    /**
     *
     * @param tmpWriter
     * @throws XMLStreamException
     */
    private void writeRectifiedGrid(XMLStreamWriter tmpWriter) throws XMLStreamException{
        tmpWriter.writeStartElement("gml:RectifiedGridCoverage");
        tmpWriter.writeAttribute("dimension","2");
        tmpWriter.writeAttribute("gml:id","RGC0001");
        tmpWriter.writeStartElement("gml:rectifiedGridDomain");
        tmpWriter.writeStartElement("gml:RectifiedGrid");
        tmpWriter.writeAttribute("dimension","2");
        tmpWriter.writeStartElement("gml:limits");
        writeGridEnvelope(tmpWriter);
        writeAxis(tmpWriter, "x");
        writeAxis(tmpWriter, "y");
        writeOrigin(tmpWriter);
        writeOffsetVector(tmpWriter, this.jp2MetadataResources.getStepX(), 0);
        writeOffsetVector(tmpWriter,0, -this.jp2MetadataResources.getStepY());
        writeEndElement(2,tmpWriter);
        writeRangeSet(tmpWriter);

    }

    /**
     *
     * @param tmpWriter
     * @throws XMLStreamException
     */
    private void  writeGridEnvelope(XMLStreamWriter tmpWriter)throws XMLStreamException{
        tmpWriter.writeStartElement("gml:GridEnvelope");
        writeImageLimits(tmpWriter, 1, 1, "low");
        writeImageLimits(tmpWriter, this.jp2MetadataResources.getImageWidth(), this.jp2MetadataResources.getImageHeight(), "high");
        writeEndElement(2,tmpWriter);
    }

    /**
     *
     * @param tmpWriter
     * @param x
     * @param y
     * @param position
     * @throws XMLStreamException
     */
    private void writeImageLimits(XMLStreamWriter tmpWriter, int x, int y, String position)throws XMLStreamException{
        tmpWriter.writeStartElement("gml:" + position);
        tmpWriter.writeCharacters(x + " " + y);
        tmpWriter.writeEndElement();
    }

    /**
     *
     * @param tmpWriter
     * @throws XMLStreamException
     */
    private void writeRangeSet(XMLStreamWriter tmpWriter)throws XMLStreamException {
        tmpWriter.writeStartElement("gml:rangeSet");
        tmpWriter.writeStartElement("gml:File");
        tmpWriter.writeStartElement("gml:fileName");
        tmpWriter.writeCharacters(this.jp2MetadataResources.getFileName());
        tmpWriter.writeEndElement();
        tmpWriter.writeStartElement("gml:fileStructure");
        tmpWriter.writeCharacters(this.jp2MetadataResources.getFileStructureType());
        writeEndElement(3,tmpWriter);
    }

    /**
     *
     * @param tmpWriter
     * @throws XMLStreamException
     */
    private void writeOrigin(XMLStreamWriter tmpWriter) throws XMLStreamException{
        tmpWriter.writeStartElement("gml:origin");
        tmpWriter.writeStartElement("gml:Point");
        tmpWriter.writeAttribute("gml:id","P0001");
        tmpWriter.writeAttribute("srsName","urn:ogc:def:crs:EPSG::" + this.jp2MetadataResources.getEpsgNumber());
        tmpWriter.writeStartElement("gml:pos");
        tmpWriter.writeCharacters(this.jp2MetadataResources.getPoint().getX() + " " + this.jp2MetadataResources.getPoint().getY());
        writeEndElement(3,tmpWriter);
    }

    /**
     *
     * @param tmpWriter
     * @param Off1
     * @param Off2
     * @throws XMLStreamException
     */
    private void writeOffsetVector(XMLStreamWriter tmpWriter, double Off1, double Off2 ) throws XMLStreamException{
        tmpWriter.writeStartElement("gml:offsetVector");
        tmpWriter.writeAttribute("srsName","urn:ogc:def:crs:EPSG::" + this.jp2MetadataResources.getEpsgNumber());
        tmpWriter.writeCharacters(Off1 + " " + Off2);
        tmpWriter.writeEndElement();
    }

    /**
     *
     * @param tmpWriter
     * @param axis
     * @throws XMLStreamException
     */
    private void writeAxis(XMLStreamWriter tmpWriter, String axis)throws XMLStreamException  {
        tmpWriter.writeStartElement("gml:axisName");
        tmpWriter.writeCharacters(axis);
        tmpWriter.writeEndElement();
    }

    /**
     *
     * @param numberOfElementsToClose
     * @param tmpXMLWriter
     * @throws XMLStreamException
     */
    private  void writeEndElement(int numberOfElementsToClose,XMLStreamWriter tmpXMLWriter) throws XMLStreamException {
        for(int index=0; index<numberOfElementsToClose;index++){
            tmpXMLWriter.writeEndElement();
        }
    }
}