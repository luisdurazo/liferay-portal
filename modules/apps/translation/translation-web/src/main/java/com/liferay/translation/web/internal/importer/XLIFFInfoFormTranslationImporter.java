/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.translation.web.internal.importer;

import com.liferay.info.field.InfoField;
import com.liferay.info.field.InfoFieldValue;
import com.liferay.info.field.type.TextInfoFieldType;
import com.liferay.info.item.InfoItemClassPKReference;
import com.liferay.info.item.InfoItemFieldValues;
import com.liferay.info.localized.InfoLocalizedValue;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Attribute;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReader;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.translation.exception.XLIFFFileException;
import com.liferay.translation.importer.TranslationInfoItemFieldValuesImporter;

import java.io.File;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.okapi.common.Event;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.exceptions.OkapiIllegalFilterOperationException;
import net.sf.okapi.common.resource.DocumentPart;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.Property;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.resource.StartDocument;
import net.sf.okapi.common.resource.StartSubDocument;
import net.sf.okapi.common.resource.TextContainer;
import net.sf.okapi.common.resource.TextFragment;
import net.sf.okapi.filters.autoxliff.AutoXLIFFFilter;
import net.sf.okapi.lib.xliff2.InvalidParameterException;
import net.sf.okapi.lib.xliff2.XLIFFException;
import net.sf.okapi.lib.xliff2.core.Fragment;
import net.sf.okapi.lib.xliff2.core.Part;
import net.sf.okapi.lib.xliff2.core.StartXliffData;
import net.sf.okapi.lib.xliff2.core.Unit;
import net.sf.okapi.lib.xliff2.document.FileNode;
import net.sf.okapi.lib.xliff2.document.XLIFFDocument;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Alejandro Tardín
 */
@Component(
	property = "content.type=application/xliff+xml",
	service = TranslationInfoItemFieldValuesImporter.class
)
public class XLIFFInfoFormTranslationImporter
	implements TranslationInfoItemFieldValuesImporter {

	@Override
	public InfoItemFieldValues importInfoItemFieldValues(
			long groupId, InfoItemClassPKReference infoItemClassPKReference,
			InputStream inputStream)
		throws IOException, XLIFFFileException {

		try {
			File tempFile = FileUtil.createTempFile(inputStream);

			String encoding = StringPool.UTF8;

			LocaleId sourceLocaleId = LocaleId.fromString(
				LocaleUtil.toLanguageId(LocaleUtil.getDefault()));

			LocaleId targetLocaleId = LocaleId.fromString(
				LocaleUtil.toLanguageId(LocaleUtil.getDefault()));

			try {
				Document document = SAXReaderUtil.read(tempFile);

				Element rootElement = document.getRootElement();

				Attribute encodingAttribute = rootElement.attribute("encoding");

				if (encodingAttribute != null) {
					encoding = encodingAttribute.getValue();
				}

				Element fileElement = rootElement.element("file");

				if (fileElement != null) {
					Attribute sourceLanguageAttribute = fileElement.attribute(
						"source-language");

					if (sourceLanguageAttribute != null) {
						sourceLocaleId = LocaleId.fromString(
							sourceLanguageAttribute.getValue());
					}

					Attribute targetLanguageAttribute = fileElement.attribute(
						"target-language");

					if (targetLanguageAttribute != null) {
						targetLocaleId = LocaleId.fromString(
							targetLanguageAttribute.getValue());
					}
				}
			}
			catch (DocumentException documentException) {
				throw new XLIFFFileException.MustBeValid(documentException);
			}

			RawDocument rawDocument = new RawDocument(
				tempFile.toURI(), encoding, sourceLocaleId, targetLocaleId);

			AutoXLIFFFilter filter = new AutoXLIFFFilter();

			try {
				filter.open(rawDocument);

				Stream<Event> stream = filter.stream();

				List<Event> events = stream.collect(Collectors.toList());

				if (_isVersion20(events)) {
					return _getInfoItemFieldValuesXLIFFv20(
						groupId, infoItemClassPKReference, tempFile);
				}

				return _getInfoItemFieldValuesXLIFFv12(
					events, infoItemClassPKReference);
			}
			catch (OkapiIllegalFilterOperationException
						okapiIllegalFilterOperationException) {

				throw new XLIFFFileException.MustBeValid(
					okapiIllegalFilterOperationException);
			}
			finally {
				filter.close();
			}
		}
		catch (InvalidParameterException invalidParameterException) {
			throw new XLIFFFileException.MustHaveValidParameter(
				invalidParameterException);
		}
		catch (XLIFFException xliffException) {
			if (xliffException.getCause() instanceof CharConversionException) {
				throw new XLIFFFileException.MustHaveCorrectEncoding(
					xliffException);
			}
			throw new XLIFFFileException.MustBeValid(xliffException);
		}
	}

	private InfoItemFieldValues _getInfoItemFieldValuesXLIFFv12(
			List<Event> events,
			InfoItemClassPKReference infoItemClassPKReference)
		throws XLIFFFileException {

		StartSubDocument startSubDocument = _getStartSubDocument(events);

		_validateDocumentPartVersion(events);

		_validateXLIFFStartSubDocument(
			infoItemClassPKReference, startSubDocument);

		Locale targetLocale = _getTargetLocale(startSubDocument);

		return InfoItemFieldValues.builder(
		).<XLIFFFileException>infoFieldValue(
			consumer -> {
				for (Event event : events) {
					if (event.isTextUnit()) {
						ITextUnit textUnit = event.getTextUnit();

						_validateWellFormedTextUnit(targetLocale, textUnit);

						for (LocaleId targetLocaleId :
								textUnit.getTargetLocales()) {

							TextContainer value = textUnit.getTarget(
								targetLocaleId);

							TextFragment firstContent = value.getFirstContent();

							InfoField infoField = new InfoField(
								TextInfoFieldType.INSTANCE,
								InfoLocalizedValue.<String>builder(
								).value(
									targetLocale, textUnit.getId()
								).build(),
								true, textUnit.getId());

							consumer.accept(
								new InfoFieldValue<>(
									infoField,
									InfoLocalizedValue.builder(
									).value(
										targetLocale, firstContent.getText()
									).build()));
						}
					}
				}
			}
		).infoItemClassPKReference(
			infoItemClassPKReference
		).build();
	}

	private InfoItemFieldValues _getInfoItemFieldValuesXLIFFv20(
			long groupId, InfoItemClassPKReference infoItemClassPKReference,
			File tempFile)
		throws XLIFFFileException {

		XLIFFDocument xliffDocument = new XLIFFDocument();

		xliffDocument.load(tempFile);

		_validateXLIFFFile(groupId, infoItemClassPKReference, xliffDocument);

		StartXliffData startXliffData = xliffDocument.getStartXliffData();

		Locale targetLocale = LocaleUtil.fromLanguageId(
			startXliffData.getTargetLanguage(), true, false);

		return InfoItemFieldValues.builder(
		).<XLIFFFileException>infoFieldValue(
			consumer -> {
				for (Unit unit : xliffDocument.getUnits()) {
					if (unit.getPartCount() != 1) {
						throw new XLIFFFileException.MustNotHaveMoreThanOne(
							"The file only can have one unit");
					}

					Part valuePart = unit.getPart(0);

					Fragment value = valuePart.getTarget();

					if (value == null) {
						throw new XLIFFFileException.MustBeWellFormed(
							"There is no translation target");
					}

						InfoField infoField = InfoField.builder(
						).infoFieldType(
							TextInfoFieldType.INSTANCE
						).name(
							unit.getId()
						).labelInfoLocalizedValue(
							InfoLocalizedValue.<String>builder(
							).value(
								targetLocale, unit.getId()
							).build()
						).localizable(
							true
						).build();

					consumer.accept(
						new InfoFieldValue<>(
							infoField,
							InfoLocalizedValue.builder(
							).value(
								targetLocale, value.getPlainText()
							).build()));
				}
			}
		).infoItemClassPKReference(
			infoItemClassPKReference
		).build();
	}

	private StartSubDocument _getStartSubDocument(List<Event> events) {
		for (Event event : events) {
			if (event.isStartSubDocument()) {
				return event.getStartSubDocument();
			}
		}

		return null;
	}

	private Locale _getTargetLocale(StartSubDocument startSubDocument) {
		Property targetLanguageProperty = startSubDocument.getProperty(
			"targetLanguage");

		if ((targetLanguageProperty == null) ||
			(targetLanguageProperty.getValue() == null)) {

			return null;
		}

		String targetLanguage = targetLanguageProperty.getValue();

		return LocaleUtil.fromLanguageId(targetLanguage);
	}

	private boolean _isVersion20(List<Event> events) {
		for (Event event : events) {
			if (event.isStartDocument()) {
				StartDocument startDocument = event.getStartDocument();

				Property versionProperty = startDocument.getProperty("version");

				if (versionProperty != null) {
					double version = GetterUtil.getDouble(
						versionProperty.getValue());

					if ((version >= 2.0) && (version < 3.0)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private void _validateDocumentPartVersion(List<Event> events)
		throws XLIFFFileException.MustBeValid {

		for (Event event : events) {
			if (event.isDocumentPart()) {
				DocumentPart documentPart = event.getDocumentPart();

				Property version = documentPart.getProperty("version");

				if ((version != null) &&
					!Objects.equals("1.2", version.getValue())) {

					throw new XLIFFFileException.MustBeValid(
						"version must be 1.2");
				}
			}
		}
	}

	private void _validateWellFormedTextUnit(
			Locale targetLocale, ITextUnit textUnit)
		throws XLIFFFileException.MustBeWellFormed {

		TextContainer source = textUnit.getSource();
		Set<LocaleId> targetLocales = textUnit.getTargetLocales();

		if (!source.isEmpty() && targetLocales.isEmpty()) {
			throw new XLIFFFileException.MustBeWellFormed(
				"There is no translation target");
		}

		if (targetLocales.size() > 1) {
			throw new XLIFFFileException.MustBeWellFormed(
				"Only one translation language per file is permitted");
		}

		for (LocaleId targetLocaleId : targetLocales) {
			if ((targetLocale != null) &&
				!Objects.equals(targetLocale, targetLocaleId.toJavaLocale())) {

				throw new XLIFFFileException.MustBeWellFormed(
					"Only one translation language per file is permitted");
			}

			TextContainer value = textUnit.getTarget(targetLocaleId);

			if (!source.isEmpty() && value.isEmpty()) {
				throw new XLIFFFileException.MustBeWellFormed(
					"There is no translation target");
			}
		}
	}

	private void _validateXLIFFCompletion(
			long groupId, XLIFFDocument xliffDocument)
		throws XLIFFFileException {

		StartXliffData startXliffData = xliffDocument.getStartXliffData();

		String sourceLanguage = startXliffData.getSourceLanguage();

		if (Validator.isNull(sourceLanguage)) {
			throw new XLIFFFileException.MustBeWellFormed(
				"There is no translation source");
		}

		Locale sourceLocale = LocaleUtil.fromLanguageId(
			sourceLanguage, true, false);

		if (sourceLocale == null) {
			throw new XLIFFFileException.MustBeSupportedLanguage(
				sourceLanguage);
		}

		Set<Locale> availableLocales = _language.getAvailableLocales(groupId);

		if (!availableLocales.contains(sourceLocale)) {
			throw new XLIFFFileException.MustBeSupportedLanguage(
				sourceLanguage);
		}

		String targetLanguage = startXliffData.getTargetLanguage();

		if (Validator.isNull(targetLanguage)) {
			throw new XLIFFFileException.MustBeWellFormed(
				"There is no translation target");
		}

		Locale targetLocale = LocaleUtil.fromLanguageId(
			targetLanguage, true, false);

		if (targetLocale == null) {
			throw new XLIFFFileException.MustBeSupportedLanguage(
				targetLanguage);
		}

		if (!availableLocales.contains(targetLocale)) {
			throw new XLIFFFileException.MustBeSupportedLanguage(
				targetLanguage);
		}
	}

	private void _validateXLIFFFile(
			long groupId, InfoItemClassPKReference infoItemClassPKReference,
			XLIFFDocument xliffDocument)
		throws XLIFFFileException {

		_validateXLIFFCompletion(groupId, xliffDocument);

		_validateXLIFFFileNode(infoItemClassPKReference, xliffDocument);
	}

	private void _validateXLIFFFileNode(
			InfoItemClassPKReference infoItemClassPKReference,
			XLIFFDocument xliffDocument)
		throws XLIFFFileException {

		List<String> fileNodeIds = xliffDocument.getFileNodeIds();

		if (fileNodeIds.size() != 1) {
			throw new XLIFFFileException.MustNotHaveMoreThanOne(
				"Only one node is allowed");
		}

		FileNode fileNode = xliffDocument.getFileNode(
			infoItemClassPKReference.getClassName() + StringPool.COLON +
				infoItemClassPKReference.getClassPK());

		if (fileNode == null) {
			throw new XLIFFFileException.MustHaveValidId("File ID is invalid");
		}
	}

	private void _validateXLIFFStartSubDocument(
			InfoItemClassPKReference infoItemClassPKReference,
			StartSubDocument startSubDocument)
		throws XLIFFFileException {

		if (startSubDocument == null) {
			throw new XLIFFFileException.MustBeWellFormed(
				"The XLIFF file is not well Formed");
		}

		String original =
			infoItemClassPKReference.getClassName() + StringPool.COLON +
				infoItemClassPKReference.getClassPK();

		if (!Objects.equals(startSubDocument.getName(), original)) {
			throw new XLIFFFileException.MustHaveValidId("File ID is invalid");
		}

		Property targetLanguageProperty = startSubDocument.getProperty(
			"targetLanguage");

		if ((targetLanguageProperty == null) ||
			(targetLanguageProperty.getValue() == null)) {

			throw new XLIFFFileException.MustBeWellFormed(
				"There is no translation target");
		}
	}

	@Reference
	private Language _language;

	@Reference
	private SAXReader _saxReader;

}