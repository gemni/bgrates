package net.vexelon.bgrates.db;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.vexelon.bgrates.Defs;
import net.vexelon.bgrates.db.models.CurrencyData;
import net.vexelon.bgrates.db.models.CurrencyLocales;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.google.common.collect.Lists;

public class SQLiteDataSource implements DataSource {

	private static final String[] ALL_COLUMNS = { Defs.COLUMN_ID, Defs.COLUMN_GOLD, Defs.COLUMN_NAME, Defs.COLUMN_CODE,
			Defs.COLUMN_RATIO, Defs.COLUMN_REVERSERATE, Defs.COLUMN_RATE, Defs.COLUMN_EXTRAINFO, Defs.COLUMN_CURR_DATE,
			Defs.COLUMN_TITLE, Defs.COLUMN_F_STAR };
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private SQLiteDatabase database;
	private CurrenciesSQLiteDB dbHelper;

	@Override
	public void connect(Context context) throws DataSourceException {
		try {
			dbHelper = new CurrenciesSQLiteDB(context);
			database = dbHelper.getWritableDatabase();
		} catch (SQLException e) {
			throw new DataSourceException("Could not open SQLite database!", e);
		}
	}

	@Override
	public void close() {
		if (dbHelper != null) {
			dbHelper.close();
		}
	}

	private Date parseStringToDate(String date, String format) throws ParseException {
		return new SimpleDateFormat(format).parse(date);
	}

	private String parseDateToString(Date date, String dateFormat) {
		return new SimpleDateFormat(dateFormat).format(date);
	}

	@Override
	public void addRates(Map<CurrencyLocales, List<CurrencyData>> rates) throws DataSourceException {
		ContentValues values = new ContentValues();
		ContentValues valuesDate = new ContentValues();

		for (Map.Entry<CurrencyLocales, List<CurrencyData>> currenciesData : rates.entrySet()) {

			// Данните от сайта на БНБ се разделят на два списъка - от динамични
			// валути и от статични валути
			List<CurrencyData> dynamicCurrencies = new ArrayList<CurrencyData>();
			List<CurrencyData> fixedCurrencies = new ArrayList<CurrencyData>();
			for (CurrencyData currency : currenciesData.getValue()) {
				if (currency.isFixed()) {
					fixedCurrencies.add(currency);
				} else {
					dynamicCurrencies.add(currency);
				}
			}

			// //За всеки от списъците се прави проверка дали го има в базата.
			// За динамични валути
			// TODO - да се ползва новия метод
			if (!isHaveRates(currenciesData.getKey(), dynamicCurrencies.get(1).getCurrDate(), false)) {
				for (int i = 0; i < dynamicCurrencies.size(); i++) {
					values.put(Defs.COLUMN_GOLD, dynamicCurrencies.get(i).getGold());
					values.put(Defs.COLUMN_NAME, dynamicCurrencies.get(i).getName());
					values.put(Defs.COLUMN_CODE, dynamicCurrencies.get(i).getCode());
					values.put(Defs.COLUMN_RATIO, dynamicCurrencies.get(i).getRatio());
					values.put(Defs.COLUMN_REVERSERATE, dynamicCurrencies.get(i).getReverseRate());
					values.put(Defs.COLUMN_RATE, dynamicCurrencies.get(i).getRate());
					values.put(Defs.COLUMN_EXTRAINFO, dynamicCurrencies.get(i).getExtraInfo());
					values.put(Defs.COLUMN_CURR_DATE,
							parseDateToString(dynamicCurrencies.get(i).getCurrDate(), DATE_FORMAT));
					values.put(Defs.COLUMN_TITLE, dynamicCurrencies.get(i).getTitle());
					values.put(Defs.COLUMN_F_STAR, dynamicCurrencies.get(i).getfStar());
					values.put(Defs.COLUMN_LOCALE, currenciesData.getKey().toString());

					database.insert(Defs.TABLE_CURRENCY, null, values);// TODO
																		// remove
																		// comment
					values = new ContentValues();

				}

				valuesDate.put(Defs.COLUMN_CURR_DATE,
						parseDateToString(currenciesData.getValue().get(1).getCurrDate(), DATE_FORMAT));
				valuesDate.put(Defs.COLUMN_LOCALE, currenciesData.getKey().toString());
				database.insert(Defs.TABLE_CURRENCY_DATE, null, valuesDate);// TODO
																			// remove
																			// comment

				valuesDate = new ContentValues();
			}

			// За фиксирани валути. Може да го има вече в базата, защото се
			// добавят веднъж годишно
			if (fixedCurrencies.size() > 0) {
				if (!isHaveRates(currenciesData.getKey(), fixedCurrencies.get(1).getCurrDate(), true)) {
					for (int i = 0; i < fixedCurrencies.size(); i++) {
						values.put(Defs.COLUMN_GOLD, fixedCurrencies.get(i).getGold());
						values.put(Defs.COLUMN_NAME, fixedCurrencies.get(i).getName());
						values.put(Defs.COLUMN_CODE, fixedCurrencies.get(i).getCode());
						values.put(Defs.COLUMN_RATIO, fixedCurrencies.get(i).getRatio());
						values.put(Defs.COLUMN_REVERSERATE, fixedCurrencies.get(i).getReverseRate());
						values.put(Defs.COLUMN_RATE, fixedCurrencies.get(i).getRate());
						values.put(Defs.COLUMN_EXTRAINFO, fixedCurrencies.get(i).getExtraInfo());
						values.put(Defs.COLUMN_CURR_DATE,
								parseDateToString(fixedCurrencies.get(i).getCurrDate(), DATE_FORMAT));
						values.put(Defs.COLUMN_TITLE, fixedCurrencies.get(i).getTitle());
						values.put(Defs.COLUMN_F_STAR, fixedCurrencies.get(i).getfStar());
						values.put(Defs.COLUMN_LOCALE, currenciesData.getKey().toString());

						database.insert(Defs.TABLE_FIXED_CURRENCY, null, values);// TODO
																					// remove
																					// comment
						values = new ContentValues();
					}
				}
			}
		}
	}

	private Boolean isHaveRates(CurrencyLocales locale, Date dateOfCurrency, boolean isFixed) {
		String[] tableColumns = new String[] { Defs.COLUMN_CURR_DATE };
		String whereClause = Defs.COLUMN_CURR_DATE + " = ? AND " + Defs.COLUMN_LOCALE + " = ? ";
		String[] whereArgs = new String[] { parseDateToString(dateOfCurrency, DATE_FORMAT), locale.toString() };

		Cursor cursor = null;
		if (isFixed) {
			cursor = database.query(Defs.TABLE_FIXED_CURRENCY, tableColumns, whereClause, whereArgs, null, null, null);
		} else {
			cursor = database.query(Defs.TABLE_CURRENCY_DATE, tableColumns, whereClause, whereArgs, null, null, null);
		}
		if (cursor.moveToFirst()) {
			cursor.close();
			return true;
		} else {
			cursor.close();
			return false;
		}
	}

	@Override
	public List<CurrencyData> getLastRates(CurrencyLocales locale) throws DataSourceException {
		List<CurrencyData> lastRates = Lists.newArrayList();
		String[] tableColumns = new String[] { Defs.COLUMN_CURR_DATE };
		String whereClause = Defs.COLUMN_LOCALE + " = ? ";
		String[] whereArgs = new String[] { locale.toString() };

		Cursor cursor = database.query(Defs.TABLE_CURRENCY_DATE, tableColumns, whereClause, whereArgs, null, null,
				Defs.COLUMN_CURR_DATE + " DESC");

		if (cursor.moveToFirst()) {
			String whereClause2 = Defs.COLUMN_CURR_DATE + " = ? AND " + Defs.COLUMN_LOCALE + " = ? ";
			String[] whereArgs2 = new String[] { cursor.getString(cursor.getColumnIndex(Defs.COLUMN_CURR_DATE)),
					locale.toString() };

			Cursor cursor2 = database.query(Defs.TABLE_CURRENCY, ALL_COLUMNS, whereClause2, whereArgs2, null, null,
					null);

			cursor2.moveToFirst();
			while (!cursor2.isAfterLast()) {
				CurrencyData comment = cursorToCurrency(cursor2, false);
				lastRates.add(comment);
				cursor2.moveToNext();
			}
			// make sure to close the cursor
			cursor2.close();
		}
		cursor.close();

		return lastRates;
	}

	@Override
	public List<CurrencyData> getLastFixedRates(CurrencyLocales locale) throws DataSourceException {
		List<CurrencyData> lastRates = Lists.newArrayList();
		try {
			String[] tableColumns = new String[] { Defs.COLUMN_CURR_DATE };
			String whereClause = Defs.COLUMN_LOCALE + " = ? ";
			String[] whereArgs = new String[] { locale.toString() };

			// Cursor cursor = database.query(Defs.TABLE_FIXED_CURRENCY,
			// tableColumns, whereClause, whereArgs, null,
			// null,Defs.COLUMN_CURR_DATE + " DESC");
			Cursor cursor = database.query(true, Defs.TABLE_FIXED_CURRENCY, tableColumns, whereClause, whereArgs, null,
					null, Defs.COLUMN_CURR_DATE + " DESC", null);

			if (cursor.moveToFirst()) {
				String whereClause2 = Defs.COLUMN_CURR_DATE + " = ? AND " + Defs.COLUMN_LOCALE + " = ? ";
				String[] whereArgs2 = new String[] { cursor.getString(cursor.getColumnIndex(Defs.COLUMN_CURR_DATE)),
						locale.toString() };

				Cursor cursor2 = database.query(Defs.TABLE_FIXED_CURRENCY, ALL_COLUMNS, whereClause2, whereArgs2, null,
						null, null);

				cursor2.moveToFirst();
				while (!cursor2.isAfterLast()) {
					CurrencyData comment = cursorToCurrency(cursor2, true);
					lastRates.add(comment);
					cursor2.moveToNext();
				}
				// make sure to close the cursor
				cursor2.close();
			}
			cursor.close();

		} catch (SQLiteException e) {
			throw new DataSourceException("Could not get last fixed rates! locale=" + locale, e);
		}
		return lastRates;
	}

	@Override
	public List<Date> getAvailableRatesDates(CurrencyLocales locale) throws DataSourceException {
		List<Date> resultCurrency = Lists.newArrayList();
		String[] tableColumns = new String[] { Defs.COLUMN_CURR_DATE };
		String whereClause = Defs.COLUMN_LOCALE + " = ? ";
		String[] whereArgs = new String[] { locale.toString() };

		Cursor cursor = database.query(true, Defs.TABLE_CURRENCY_DATE, tableColumns, whereClause, whereArgs, null,
				null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			try {
				resultCurrency.add(parseStringToDate(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_CURR_DATE)),
						DATE_FORMAT));
			} catch (ParseException e) {
				// TODO: proper handling of date
				e.printStackTrace();
			}
			cursor.moveToNext();
		}
		// make sure to close the cursor
		cursor.close();
		return resultCurrency;
	}

	// TODO - new method...
	@Override
	public List<CurrencyData> getRates(CurrencyLocales locale, Date dateOfCurrency) throws DataSourceException {
		List<CurrencyData> resultCurrency = new ArrayList<CurrencyData>();
		String whereClause = Defs.COLUMN_CURR_DATE + " = ? AND " + Defs.COLUMN_LOCALE + " = ? ";
		String[] whereArgs = new String[] { parseDateToString(dateOfCurrency, DATE_FORMAT), locale.toString() };

		Cursor cursor = database.query(Defs.TABLE_CURRENCY, ALL_COLUMNS, whereClause, whereArgs, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			CurrencyData comment = cursorToCurrency(cursor, false);
			resultCurrency.add(comment);
			cursor.moveToNext();
		}
		// make sure to close the cursor
		cursor.close();
		return resultCurrency;
	}

	@Override
	public List<CurrencyData> getFixedRates(CurrencyLocales locale, Date dateOfCurrency) throws DataSourceException {
		List<CurrencyData> resultFixedCurrency = null;
		Cursor cursor = null;
		try {
			resultFixedCurrency = new ArrayList<CurrencyData>();
			String whereClause = Defs.COLUMN_CURR_DATE + " = ? AND " + Defs.COLUMN_LOCALE + " = ? ";
			String[] whereArgs = new String[] { parseDateToString(dateOfCurrency, DATE_FORMAT), locale.toString() };

			cursor = database.query(Defs.TABLE_FIXED_CURRENCY, ALL_COLUMNS, whereClause, whereArgs, null, null, null);

			cursor.moveToFirst();
			while (!cursor.isAfterLast()) {
				CurrencyData comment = cursorToCurrency(cursor, false);
				resultFixedCurrency.add(comment);
				cursor.moveToNext();
			}
			// make sure to close the cursor
			cursor.close();

		} catch (SQLiteException e) {
			throw new DataSourceException("Could not get fixed rates! locale=" + locale, e);
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return resultFixedCurrency;
	}

	@Override
	public List<CurrencyData> getRates(CurrencyLocales locale) throws DataSourceException {
		List<CurrencyData> currencies = new ArrayList<CurrencyData>();
		String whereClause = Defs.COLUMN_LOCALE + " = ? ";
		String[] whereArgs = new String[] { locale.toString() };

		Cursor cursor = database.query(Defs.TABLE_CURRENCY, ALL_COLUMNS, whereClause, whereArgs, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			CurrencyData comment = cursorToCurrency(cursor, false);
			currencies.add(comment);
			cursor.moveToNext();
		}
		// make sure to close the cursor
		cursor.close();
		return currencies;
	}

	private CurrencyData cursorToCurrency(Cursor cursor, boolean isFixed) {
		CurrencyData currency = new CurrencyData();
		currency.setGold(cursor.getInt(cursor.getColumnIndex(Defs.COLUMN_GOLD)));
		currency.setName(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_NAME)));
		currency.setCode(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_CODE)));
		currency.setRatio(cursor.getInt(cursor.getColumnIndex(Defs.COLUMN_RATIO)));
		currency.setReverseRate(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_REVERSERATE)));
		currency.setRate(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_RATE)));
		currency.setExtraInfo(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_EXTRAINFO)));
		try {
			currency.setCurrDate(parseStringToDate(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_CURR_DATE)),
					DATE_FORMAT));
		} catch (ParseException e) {
			// TODO: proper handling of date
			e.printStackTrace();
		}
		currency.setTitle(cursor.getString(cursor.getColumnIndex(Defs.COLUMN_TITLE)));
		currency.setfStar(cursor.getInt(cursor.getColumnIndex(Defs.COLUMN_F_STAR)));
		currency.setIsFixed(isFixed);
		return currency;
	}
}
