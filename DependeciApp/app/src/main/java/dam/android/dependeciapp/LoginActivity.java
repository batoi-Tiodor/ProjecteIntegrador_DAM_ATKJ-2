package dam.android.dependeciapp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

import dam.android.dependeciapp.Controladores.Conexion;
import dam.android.dependeciapp.Controladores.SQLite.DependenciaDBManager;
import dam.android.dependeciapp.Pojo.Usuario;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */

    private static final int REQUEST_MAPS = 1;
    private final String[] PERMISSIONS_MAPS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    private UserLoginTask mAuthTask = null;
    private static final String MYPREFS = "LoginPreferences";
    // UI references.
    private EditText etDNI;
    private EditText etPass;
    private View mProgressView;
    private View mLoginFormView;
    private Conexion con;
    private CheckBox cbGuardaUsuarioPass;
    private CheckBox cbIniciaSesion;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mProgressView = findViewById(R.id.login_progress);
        mLoginFormView = findViewById(R.id.login_form);
        askForMapPermission();
        Intent i = getIntent();
        boolean hasCerradoSesion = false;
        if (i != null)
            hasCerradoSesion = i.getBooleanExtra("CIERRA_SESION", false);
        if (hasCerradoSesion)
            borrarPreferencias();
        boolean seHaIniciado = IniciaSesionAutomaticamente();
        //Si la sesion se inicia automaticamente no se cargan ni la UI ni las preferencais
        //para ahorrar recursos y tiempo
        if (!seHaIniciado) {
            // setContentView(R.layout.activity_login);
            setUI();
            cargaPreferencias();
        }
    }


    private void setUI() {
        cbGuardaUsuarioPass = (CheckBox) findViewById(R.id.cbGuarda);
        cbIniciaSesion = (CheckBox) findViewById(R.id.cbSesion);
        cbIniciaSesion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cbGuardaUsuarioPass.setChecked(true);
            }
        });
        etDNI = (EditText) findViewById(R.id.DNI);
        etPass = (EditText) findViewById(R.id.password);
        Button btLogin = (Button) findViewById(R.id.DNI_sign_in_button);
        btLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                intentaLogear();
            }
        });

    }

    private void askForMapPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_MAPS, REQUEST_MAPS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_MAPS) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED && grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.maps_right_required, Toast.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void intentaLogear() {
        if (mAuthTask != null) {
            return;
        }
        showProgress(true);

        etDNI.setError(null);
        etPass.setError(null);

        String DNI = etDNI.getText().toString();
        String password = etPass.getText().toString();

        boolean cancel = false;
        View focusView = null;

        if (TextUtils.isEmpty(password)) {
            etPass.setError(getString(R.string.error_invalid_password));
            focusView = etPass;
            cancel = true;
        }
        if (TextUtils.isEmpty(DNI)) {
            cancel = true;
            focusView = etDNI;
            Toast.makeText(this, R.string.error_field_required, Toast.LENGTH_LONG).show();

        } else if (!dniValido(DNI.trim())) {
            Toast.makeText(this, R.string.error_invalid_DNI, Toast.LENGTH_LONG).show();
            focusView = etDNI;
            cancel = true;
        }
        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            showProgress(false);
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            mAuthTask = new UserLoginTask();
            mAuthTask.execute(DNI.trim(), password);
            try {
                if (!mAuthTask.get())
                    Toast.makeText(this, R.string.no_inicia_sesion, Toast.LENGTH_LONG).show();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

        }
    }

    //Metodo para comprobar que un DNI es real
    private boolean dniValido(String dniAComprobar) {
        char[] letraDni = {
                'T', 'R', 'W', 'A', 'G', 'M', 'Y', 'F', 'P', 'D', 'X', 'B', 'N', 'J', 'Z', 'S', 'Q', 'V', 'H', 'L', 'C', 'K', 'E'
        };
        String num = "";
        int ind = 0;
        if (dniAComprobar.length() == 8) {
            dniAComprobar = "0" + dniAComprobar;
        }
        if (!Character.isLetter(dniAComprobar.charAt(8))) {
            return false;
        }
        if (dniAComprobar.length() != 9) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            if (!Character.isDigit(dniAComprobar.charAt(i))) {
                return false;
            }
            num += dniAComprobar.charAt(i);
        }
        ind = Integer.parseInt(num);
        ind %= 23;
        if ((Character.toUpperCase(dniAComprobar.charAt(8))) != letraDni[ind]) {
            return false;
        }
        return true;
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (mLoginFormView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                        show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                    }
                });

                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                mProgressView.animate().setDuration(shortAnimTime).alpha(
                        show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                    }
                });
            } else {
                // The ViewPropertyAnimator APIs are not available, so simply show
                // and hide the relevant UI components.
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        }
    }

    //Si el CheckBox guarda usuario contraseña esta pulsado se ejecutara
    private void GuardaUsuarioPass(String usuario, String pass) {
        SharedPreferences pref = getSharedPreferences(MYPREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("user", usuario);
        editor.putString("pass", pass);
        editor.putBoolean("guardaUserPass", true);
        //Si es null significa que se ha iniciado sesion automaticamente, no ha pasado por SetUI
        if (cbGuardaUsuarioPass == null)
            editor.putBoolean("iniciaSesion", true);
        else {
            //Si se ha marcado el CheckBox de inicia sesion, se guardan
            if (cbIniciaSesion.isChecked())
                editor.putBoolean("iniciaSesion", true);
        }
        editor.commit();
    }

    //Carga las preferencias guardadas, si no las hay carga las predeterminadas
    private void cargaPreferencias() {
        SharedPreferences prefs = getSharedPreferences(MYPREFS, MODE_PRIVATE);
        cbGuardaUsuarioPass.setChecked(prefs.getBoolean("guardaUserPass", false));
        etDNI.setText(prefs.getString("user", ""));
        etPass.setText(prefs.getString("pass", ""));
    }

    private void borrarPreferencias() {
        SharedPreferences pref = getSharedPreferences(MYPREFS, MODE_PRIVATE);
        pref.edit().clear().commit();
    }

    //Si en las prefrencias pone que se inicie sesion automaticamente se trata de iniciar la sesion
    //y devuelve un booleano para ver si iniciamos el SetUi
    private boolean IniciaSesionAutomaticamente() {
        SharedPreferences prefs = getSharedPreferences(MYPREFS, MODE_PRIVATE);
        boolean iniciaSesion = prefs.getBoolean("iniciaSesion", false);
        if (iniciaSesion) {
            String DNI = prefs.getString("user", "");
            String pass = prefs.getString("pass", "");
            mAuthTask = new UserLoginTask();
            mAuthTask.execute(DNI, pass);
            return true;
        }
        return false;
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<String, Void, Boolean> {
        private Usuario user;

        @Override
        protected Boolean doInBackground(String... strings) {
            String usuario = strings[0];
            String pass = strings[1];
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo actNetInfo = connectivityManager.getActiveNetworkInfo();
            if (actNetInfo != null && actNetInfo.isConnected())
                con = new Conexion(true);

            if (con != null) {
                return iniciaSesionOnline(usuario, pass);

            } else {
                return iniciaSesionOffline(usuario, pass);
            }
        }

        private boolean iniciaSesionOnline(String usuario, String pass) {
            ResultSet rs = con.IniciaSesion(usuario, pass);
            if (rs != null) {
                try {
                    //Si es null significa que se ha iniciado sesion automaticamente, no ha pasado por SetUI
                    if (cbGuardaUsuarioPass == null)
                        GuardaUsuarioPass(usuario, pass);
                    else {
                        //Si se ha marcado el CheckBox de guardar usuari y contraseña, se guardan
                        if (cbGuardaUsuarioPass.isChecked())
                            GuardaUsuarioPass(usuario, pass);
                            //Y si se ha marcado el inicia sesion tambien se guardan
                        else if (cbIniciaSesion.isChecked())
                            GuardaUsuarioPass(usuario, pass);
                    }
                    //A partir del result set se crea el Usuario, que sera enviado al MainActivity
                    this.user = new Usuario(rs);
                    this.user.setPass(pass);
                    //Y lo introduciomos en la SQLite
                    DependenciaDBManager.UsuarioDBManager db = new DependenciaDBManager.UsuarioDBManager(getApplicationContext());
                    db.delete(String.valueOf(user.getIdPersona()));
                    db.insert(user.getIdPersona(), user.getDNI(), user.getNombre(), user.getApellidos(), user.getfNacimiento().toString(), user.getGenero(), user.getTipoDeDependiente(), user.getfAlta().toString(), user.getPass());
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                return true;
            } else
                return false;
        }

        private boolean iniciaSesionOffline(String user, String pass) {
            DependenciaDBManager.UsuarioDBManager db = new DependenciaDBManager.UsuarioDBManager(getApplicationContext());
            Cursor cursor = db.getRows();
            if (cursor != null) {
                cursor.moveToFirst();
                if (cursor.getCount() > 0) {
                    String userSQL = cursor.getString(1);
                    String passSQL = cursor.getString(8);
                    if (user.toUpperCase().equals(userSQL) && pass.equals(passSQL)) {
                        this.user = new Usuario(cursor);
                        // GuardaUsuarioPass(user, pass);
                        return true;
                    }
                }
            }
            return false;
        }

        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);
            //Si el logeo es correcto se crea el Intento del MainActivity y le pasamos el Usuario
            if (success) {
                Intent i = new Intent(getApplicationContext(), MainActivity.class);
                i.putExtra("user", user);
                startActivity(i);
                con = null;
                finish();
            } else {
                if (etPass != null) {
                    //etPass.setError(getString(R.string.error_incorrect_password));
                    //etPass.requestFocus();
                }
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}
