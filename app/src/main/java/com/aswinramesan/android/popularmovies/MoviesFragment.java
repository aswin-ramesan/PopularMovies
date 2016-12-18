package com.aswinramesan.android.popularmovies;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A placeholder fragment containing a simple view.
 */
public class MoviesFragment extends Fragment {

    private MovieAdapter mMoviesAdapter;

    public MoviesFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mMoviesAdapter = new MovieAdapter(
                getActivity(),
                new ArrayList<Movie>()
        );

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        GridView moviesGrid = (GridView) rootView.findViewById(R.id.gridview_movies);
        moviesGrid.setAdapter(mMoviesAdapter);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        FetchMoviesTask moviesTask = new FetchMoviesTask();
        moviesTask.execute("popularity.desc");
    }

    public class FetchMoviesTask extends AsyncTask<String, Void, Movie[]> {

        private final String LOG_TAG = FetchMoviesTask.class.getSimpleName();

        public FetchMoviesTask() {
        }

        @Override
        protected Movie[] doInBackground(String... sortBy) {

            if (sortBy.length == 0) {
                return null;
            }

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String moviesJsonStr = null;
            String format = "json";

            try {
                final String TMDB_BASE_URL = "https://api.themoviedb.org/3/discover/movie?";
                final String FORMAT_PARAM = "mode";
                final String QUERY_PARAM = "sort_by";
                final String APPID_PARAM = "api_key";

                Uri builtUri = Uri.parse(TMDB_BASE_URL).buildUpon()
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(QUERY_PARAM, sortBy[0])
                        .appendQueryParameter(APPID_PARAM, BuildConfig.TMDB_API_KEY)
                        .build();

                URL url = new URL(builtUri.toString());

                // Log.v(LOG_TAG, "Built URI " + builtUri.toString());

                // Create the request to TMDB, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                moviesJsonStr = buffer.toString();

//                Log.v(LOG_TAG, "Movies JSON string: " + moviesJsonStr);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the movies data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getMoviesDataFromJson(moviesJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }

            return null;
        }

        private Movie[] getMoviesDataFromJson(String moviesJsonStr)
                throws JSONException, ParseException {
            // These are the names of the JSON objects that need to be extracted.
            final String TMDB_RESULTS = "results";
            final String TMDB_ID = "id";
            final String TMDB_TITLE = "title";
            final String TMDB_POSTER_PATH = "poster_path";
            final String TMDB_OVERVIEW = "overview";
            final String TMDB_RELEASE_DATE = "release_date";
            final String TMDB_USER_RATING = "vote_average";

            JSONObject moviesJson = new JSONObject(moviesJsonStr);
            JSONArray moviesArray = moviesJson.getJSONArray(TMDB_RESULTS);

            Movie[] movies = new Movie[moviesArray.length()];

            for (int i = 0; i < moviesArray.length(); i++) {

                // Get the JSON object representing the movie
                JSONObject movieResult = moviesArray.getJSONObject(i);
                movies[i].id = movieResult.getInt(TMDB_ID);
                movies[i].title = movieResult.getString(TMDB_TITLE);
                movies[i].thumbnailUrl = getThumbnailUrl(movieResult.getString(TMDB_POSTER_PATH));
                movies[i].overview = movieResult.getString(TMDB_OVERVIEW);
                movies[i].releaseDate = getFormattedDate(movieResult.getString(TMDB_RELEASE_DATE));
                movies[i].userRating = movieResult.getDouble(TMDB_USER_RATING);
            }

            return movies;
        }

        private String getFormattedDate(String jsonDate)
                throws java.text.ParseException {

            Date dateToFormat = new SimpleDateFormat("yyyy-MM-dd").parse(jsonDate);

            String formattedDate = new SimpleDateFormat("dd MMMM yyyy").format(dateToFormat);
            return formattedDate;
        }

        private String getThumbnailUrl(String posterPath) {

            String imageSize = "w185";
            Uri builtUri = null;

            try {
                final String TMDB_IMAGE_BASE_URL = "http://image.tmdb.org/t/p/";

                builtUri = Uri.parse(TMDB_IMAGE_BASE_URL).buildUpon()
                        .appendPath(imageSize)
                        .appendPath(posterPath)
                        .build();
            } catch (Exception e) {
            }

            return builtUri.toString();
        }

        @Override
        protected void onPostExecute(Movie[] movies) {
            if (movies.length != 0) {
                mMoviesAdapter.clear();
                for (int i = 0; i < movies.length; i++) {
                    mMoviesAdapter.add(movies[i]);
                }
            }

            super.onPostExecute(movies);
        }
    }
}
