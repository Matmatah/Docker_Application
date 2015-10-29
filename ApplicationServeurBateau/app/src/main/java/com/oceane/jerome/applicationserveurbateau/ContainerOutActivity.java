package com.oceane.jerome.applicationserveurbateau;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.util.ArrayList;


public class ContainerOutActivity extends AppCompatActivity
{

    private DataInputStream dis;
    private DataOutputStream dos;
    private String reponse;
    private ArrayList<Container> ListeContainersRecherche = null;
    private ListView ListeContainersGraphique;
    private ArrayAdapter<Container> adapter;
    private ProgressBar progressbar;
    private int cptProgress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_container_out);

        try
        {
            dis = new DataInputStream(new BufferedInputStream(LoginActivity.cliSock.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(LoginActivity.cliSock.getOutputStream()));
        }
        catch (IOException e)
        {
            System.err.println("ContainerOutActivity : Erreur de création de dis et dos : " + e);
        }

        Button bRechercher = (Button)findViewById(R.id.ButtonRechercher);
        bRechercher.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                Rechercher();
            }
        });

        ListeContainersGraphique = (ListView)(findViewById(R.id.listViewContainers));
        progressbar = (ProgressBar)(findViewById(R.id.progressBar));

        ListeContainersGraphique.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SortieContainer((Container) (ListeContainersGraphique.getItemAtPosition(position)));
            }
        });

        Button bTerminer = (Button)findViewById(R.id.ButtonTerminer);
        bTerminer.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                Terminer();
            }
        });
    }

    private void Rechercher()
    {
        final Handler h = new Handler()
        {
            public void handleMessage(Message msg)
            {
                if (msg.obj.equals("OK"))
                {
                    String[] tuples = reponse.split("#");

                    for(int i = 0; i < tuples.length; i++)
                    {
                        String[]champs = tuples[i].split("$");
                        Container c = new Container(champs[0], champs[1], champs[2], champs[3], champs[4]);
                        ListeContainersRecherche.add(c);
                    }

                    adapter = new ArrayAdapter<Container>(ContainerOutActivity.this, android.R.layout.simple_list_item_1, ListeContainersRecherche);
                    ListeContainersGraphique.setAdapter(adapter);
                    progressbar.setMax(ListeContainersRecherche.size());
                }

                else
                    Toast.makeText(getApplicationContext(), "PROBLEME : Recherche des containers : " + msg.toString(), Toast.LENGTH_LONG).show();
            }
        };

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Message msg = h.obtainMessage();

                String d = ((TextView) (findViewById(R.id.TextFieldDestination))).getText().toString();

                String c;
                if ((findViewById(R.id.checkBoxTri)).isSelected())
                    c = "FIRST";
                else
                    c = "RANDOM";

                if (!d.isEmpty())
                {
                    SendMsg("GET_CONTAINERS#" + d + "#" + c, msg);

                    reponse = ReceiveMsg(msg);
                    h.sendMessage(msg);
                }
                else
                    System.err.println("REMPLIR TOUS LES CHAMPS !");
            }
        }).start();
    }

    private void SortieContainer(Container c)
    {
        final Container curCont = c;

        final Handler h = new Handler()
        {
            public void handleMessage(Message msg)
            {
                if (msg.obj.equals("OK"))
                {
                    if (reponse.equals("OUI"))
                    {
                        ListeContainersRecherche.remove(curCont);
                        adapter.notifyDataSetChanged();
                        cptProgress++;
                        progressbar.setProgress(cptProgress);
                        Toast.makeText(getApplicationContext(), "Le container choisi n'est pas le premier de la liste : " + msg.toString(), Toast.LENGTH_LONG).show();
                    }
                    else
                        Toast.makeText(getApplicationContext(), "Le container choisi n'est pas le premier de la liste : " + msg.toString(), Toast.LENGTH_LONG).show();
                }

                else
                    Toast.makeText(getApplicationContext(), "PROBLEME : Container chargé : " + msg.toString(), Toast.LENGTH_LONG).show();
            }
        };

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Message msg = h.obtainMessage();

                SendMsg("HANDLE_CONTAINER_OUT#" + curCont.getId() + "#" + curCont.getX() + "#" + curCont.getY(), msg);

                reponse = ReceiveMsg(msg);
                h.sendMessage(msg);
            }
        }).start();
    }

    private void Terminer()
    {
        final Handler h = new Handler()
        {
            public void handleMessage(Message msg)
            {
                if (msg.obj.equals("OK"))
                {
                    if (reponse.equals("OUI"))
                    {
                        Toast.makeText(getApplicationContext(), "FICHIER A JOUR !", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(ContainerOutActivity.this, MenuActivity.class);
                        startActivity(intent);
                    }
                    else
                        Toast.makeText(getApplicationContext(), "FICHIER PAS MIS A JOUR !", Toast.LENGTH_LONG).show();
                }

                else
                    Toast.makeText(getApplicationContext(), "PROBLEME : Ecriture fichier parc : " + msg.toString(), Toast.LENGTH_LONG).show();
            }
        };

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Message msg = h.obtainMessage();
                SendMsg("END_CONTAINER_OUT#", msg);

                reponse = ReceiveMsg(msg);
                h.sendMessage(msg);
            }
        }).start();
    }

    public void SendMsg(String chargeUtile, Message msg)
    {
        int taille = chargeUtile.length();
        String message = String.valueOf(taille) + "#" + chargeUtile;

        try
        {
            dos.write(message.getBytes());
            dos.flush();
            if (msg != null)
                msg.obj = "OK";
        }
        catch(IOException e)
        {
            System.err.println("ContainerOutActivity : Erreur d'envoi de msg (IO) : " + e);
            if (msg != null)
                msg.obj = "KO" + e.getMessage();
        }
    }

    public String ReceiveMsg(Message msg)
    {
        byte b;
        StringBuffer taille = new StringBuffer();
        StringBuffer message = new StringBuffer();

        try
        {
            while ((b = dis.readByte()) != (byte)'#')
            {
                if (b != (byte)'#')
                    taille.append((char)b);
            }

            for (int i = 0; i < Integer.parseInt(taille.toString()); i++)
            {
                b = dis.readByte();
                message.append((char)b);
            }
            msg.obj = "OK";
        }
        catch(IOException e)
        {
            System.err.println("ContainerOutActivity : Erreur de reception de msg (IO) : " + e);
            msg.obj = "KO" + e.getMessage();
        }

        return message.toString();
    }
}
