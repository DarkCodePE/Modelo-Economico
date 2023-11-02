package ms.hispam.budget.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersByProjection;


import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import org.apache.commons.codec.binary.Base64;
import java.util.List;
import java.util.Objects;

public class Shared {

    private Shared(){
    }
    //@Value("${password.crypth}")
    private static final String vkey="d88E76na32VEY%ju2H36mhKRV!Gr7Jpo";


    static final String TYPEMONTH="yyyyMM";


    public static List<MonthProjection> generateMonthProjection(String monthBase, int range, BigDecimal amount) {
        List<MonthProjection> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TYPEMONTH);
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter).plusMonths(1);
        for (int i = 0; i < range; i++) {
            dates.add( MonthProjection.builder().month( fechaActual.format(formatter)).amount(amount).build() );
            fechaActual = fechaActual.plusMonths(1);
        }

        return dates;
    }
    public static List<String> generateRangeMonth(String monthBase, int range) {
        List<String> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TYPEMONTH);
        DateTimeFormatter formatterMonthName = DateTimeFormatter.ofPattern("MMM uuuu");
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter).plusMonths(1);
        for (int i = 0; i < range; i++) {
            dates.add( fechaActual.format(formatterMonthName) );
            fechaActual = fechaActual.plusMonths(1);
        }

        return dates;
    }
    public static String nameMonth(String monthBase) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TYPEMONTH);
        DateTimeFormatter formatterMonthName = DateTimeFormatter.ofPattern("MMM uuuu");
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter);

        return fechaActual.format(formatterMonthName);

    }

    public static int compare(String date1, String date2) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TYPEMONTH);
        if(Objects.equals(date1, "")){date1="290001";}
        if(Objects.equals(date2, "")){date2="290001";}

        YearMonth yearMonth1 = YearMonth.parse(date1, formatter);
        YearMonth yearMonth2 = YearMonth.parse(date2, formatter);

        return yearMonth1.compareTo(yearMonth2);
    }

    public static Integer getIndex(List<String> months , String filter){
        return months.stream().filter(p->p.equalsIgnoreCase(filter)
        ).mapToInt(months::indexOf).findFirst().orElse(-1);
    }

    public static boolean verificarMesEnRango(String rango, String mes) {
        String[] partesRango = rango.split("-");
        String mesInicioStr = partesRango[0];
        String mesFinStr = partesRango[1];

        YearMonth mesInicio = YearMonth.parse(mesInicioStr, DateTimeFormatter.ofPattern(TYPEMONTH));
        YearMonth mesFin = YearMonth.parse(mesFinStr, DateTimeFormatter.ofPattern(TYPEMONTH));
        YearMonth mesVerificar = YearMonth.parse(mes, DateTimeFormatter.ofPattern(TYPEMONTH));

        return !mesVerificar.isBefore(mesInicio) && !mesVerificar.isAfter(mesFin);
    }

    public static Double getDoubleWithDecimal(Double value){
        return Math.round(value * 100d) / 100d;
    }
    public static void replaceSLash( ParametersByProjection projection) {
        projection.setPeriod(projection.getPeriod().replace("/",""));
        projection.setNominaFrom(projection.getNominaFrom().replace("/",""));
        projection.setNominaTo(projection.getNominaTo().replace("/",""));
        projection.getParameters().forEach(k-> {
            k.setPeriod(k.getPeriod().replace("/",""));
            k.setRange(k.getRange().replace("/",""));
            k.setPeriodRetroactive(k.getPeriodRetroactive().replace("/",""));
        });
    }

    public static int contarMesesEnRango(String inicio, String fin) {
        YearMonth fechaInicio = YearMonth.parse(inicio);
        YearMonth fechaFin = YearMonth.parse(fin);

        long meses = ChronoUnit.MONTHS.between(fechaInicio, fechaFin);

        // Agregamos 1 ya que ChronoUnit.MONTHS excluye el mes de inicio, pero queremos incluirlo.
        return (int) meses + 1;
    }

    public static String encriptar(String texto)  {
        try {
            SecretKeySpec key = new SecretKeySpec(vkey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] textoEncriptado = cipher.doFinal(texto.getBytes());
            return Base64.encodeBase64String(textoEncriptado);
        }catch (Exception ex){
            return "0";
        }

    }

    public static String desencriptar(String textoEncriptado)  {
        try {
            SecretKeySpec key = new SecretKeySpec(vkey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] textoBytes = Base64.decodeBase64(textoEncriptado);
            byte[] textoDesencriptado = cipher.doFinal(textoBytes);
            return new String(textoDesencriptado);
        }catch (Exception ex){
            return "0";
        }

    }

    public static String convertArrayListToJson(Object arrayList) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(arrayList);
        } catch (Exception e) {
            return null;
        }
    }


}
