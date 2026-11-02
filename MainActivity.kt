package ir.hesabyar.app

import android.app.*
import android.os.*
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.graphics.Typeface
import android.view.*
import android.widget.*
import android.text.InputType
import android.provider.MediaStore
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.nio.charset.StandardCharsets

data class Product(val id:Long,val name:String,val price:Long)
data class InvoiceRow(val name:String,val qty:Long,val total:Long) { val avg:Long get()=if(qty==0L)0 else total/qty }

class DB(c:Context):SQLiteOpenHelper(c,"hesabyar.db",null,1) {
 override fun onCreate(d:SQLiteDatabase){ d.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price INTEGER NOT NULL)")
 d.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,qty INTEGER NOT NULL,price INTEGER NOT NULL)") }
 override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){}
 fun products():List<Product>{ val a=mutableListOf<Product>(); readableDatabase.rawQuery("SELECT id,name,price FROM products ORDER BY id",null).use{while(it.moveToNext())a.add(Product(it.getLong(0),it.getString(1),it.getLong(2)))};return a }
 fun saveProduct(id:Long,name:String,price:Long){val v=ContentValues().apply{put("name",name);put("price",price)};if(id==0L)writableDatabase.insert("products",null,v) else writableDatabase.update("products",v,"id=?", arrayOf(id.toString()))}
 fun deleteProduct(id:Long){writableDatabase.delete("products","id=?",arrayOf(id.toString()))}
 fun addSale(name:String,qty:Long,price:Long){val v=ContentValues().apply{put("name",name);put("qty",qty);put("price",price)};writableDatabase.insert("sales",null,v)}
 fun invoice():List<InvoiceRow>{val a=mutableListOf<InvoiceRow>();readableDatabase.rawQuery("SELECT name,SUM(qty),SUM(qty*price) FROM sales GROUP BY name ORDER BY name",null).use{while(it.moveToNext())a.add(InvoiceRow(it.getString(0),it.getLong(1),it.getLong(2)))};return a}
 fun clearSales(){writableDatabase.delete("sales",null,null)}
}

class MainActivity:Activity(){
 lateinit var db:DB; lateinit var body:LinearLayout
 private val navy=0xff17324d.toInt(); private val teal=0xff087e8b.toInt()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=DB(this);window.statusBarColor=navy;showHome()}
 fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
 fun text(s:String,size:Float=16f,bold:Boolean=false):TextView=TextView(this).apply{ text=s;textSize=size;setTextColor(0xff182533.toInt());if(bold)setTypeface(null,Typeface.BOLD);gravity=Gravity.RIGHT }
 fun button(s:String,fn:()->Unit)=Button(this).apply{text=s;setOnClickListener{fn()}}
 fun field(hint:String,number:Boolean=false)=EditText(this).apply{this.hint=hint;textSize=16f;inputType=if(number)InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT;gravity=Gravity.RIGHT;setSingleLine(true)}
 fun base(title:String){val sc=ScrollView(this);val outer=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(12),dp(16),dp(20));layoutDirection=View.LAYOUT_DIRECTION_RTL};sc.addView(outer);setContentView(sc)
 outer.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo); scaleType=ImageView.ScaleType.FIT_CENTER; layoutParams=LinearLayout.LayoutParams(-1,dp(150)) })
 outer.addView(text("سیب فود",25f,true));outer.addView(text(title,18f,true).apply{setTextColor(teal);setPadding(0,dp(8),0,dp(12))})
 val nav=LinearLayout(this).apply{orientation=0;layoutDirection=View.LAYOUT_DIRECTION_RTL}
 listOf("کالاها" to {showProducts()},"ثبت سفارش" to {showOrder()},"فاکتور روز" to {showInvoice()}).forEach{(t,f)->nav.addView(button(t,f),LinearLayout.LayoutParams(0,dp(48),1f))}
 outer.addView(nav);body=LinearLayout(this).apply{orientation=1;layoutDirection=View.LAYOUT_DIRECTION_RTL};outer.addView(body)
 }
 fun showHome(){showProducts()}
 fun showProducts(){base("مدیریت کالاها");val name=field("نام کالا");val price=field("قیمت واحد",true);body.addView(name);body.addView(price);body.addView(button("افزودن کالا"){val n=name.text.toString().trim();val p=price.text.toString().toLongOrNull();if(n.isEmpty()||p==null){toast("نام و قیمت معتبر وارد کنید");return@button};db.saveProduct(0,n,p);showProducts()})
 body.addView(text("فهرست کالاها",18f,true));db.products().forEach{p->val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL};row.addView(text("${p.name}  |  ${p.price}",15f),LinearLayout.LayoutParams(0,dp(52),1f));row.addView(button("ویرایش"){editProduct(p)});row.addView(button("حذف"){AlertDialog.Builder(this).setMessage("کالا حذف شود؟").setNegativeButton("انصراف",null).setPositiveButton("حذف"){_,_->db.deleteProduct(p.id);showProducts()}.show()});body.addView(row)}
 }
 fun editProduct(p:Product){val n=field("نام کالا").apply{setText(p.name)};val pr=field("قیمت واحد",true).apply{setText(p.price.toString())};val box=LinearLayout(this).apply{orientation=1;setPadding(dp(20),0,dp(20),0);addView(n);addView(pr)};AlertDialog.Builder(this).setTitle("ویرایش کالا").setView(box).setNegativeButton("انصراف",null).setPositiveButton("ذخیره"){_,_->val price=pr.text.toString().toLongOrNull();if(n.text.isNotBlank()&&price!=null)db.saveProduct(p.id,n.text.toString(),price);showProducts()}.show()}
 fun showOrder(){base("ثبت سفارش");val ps=db.products();if(ps.isEmpty()){body.addView(text("هنوز کالایی ثبت نشده است. ابتدا از بخش کالاها، کالا و قیمت آن را اضافه کنید."));return}
 val inputs=mutableListOf<Triple<Product,EditText,EditText>>()
 ps.forEach{p->body.addView(text(p.name,17f,true));val q=field("تعداد",true);val price=field("قیمت واحد برای این سفارش",true).apply{setText(p.price.toString())};body.addView(q);body.addView(price);inputs.add(Triple(p,q,price))}
 body.addView(button("ثبت سفارش"){var count=0;for((p,q,pr) in inputs){val qty=q.text.toString().toLongOrNull()?:0;val price=pr.text.toString().toLongOrNull();if(qty>0&&price!=null){db.addSale(p.name,qty,price);count++}};if(count==0)toast("تعداد حداقل یک کالا را وارد کنید") else {toast("سفارش ثبت شد");showInvoice()}})
 }
 fun showInvoice(){base("فاکتور روزانه");val rows=db.invoice();if(rows.isEmpty()){body.addView(text("فاکتور هنوز خالی است."))}else{body.addView(text("نام کالا     | تعداد | میانگین قیمت | مبلغ کل",13f,true));rows.forEach{r->body.addView(text("${r.name}\n${r.qty} عدد  |  ${r.avg}  |  ${r.total}",15f).apply{setPadding(0,dp(8),0,dp(8))})};body.addView(text("جمع کل: ${rows.sumOf{it.total}}",20f,true).apply{setPadding(0,dp(12),0,dp(12))})}
 body.addView(button("دریافت فایل Excel (XLSX)"){exportXlsx(rows)});body.addView(button("پاک‌کردن فاکتور روز"){AlertDialog.Builder(this).setMessage("تمام سفارش‌های فاکتور حذف شوند؟ این کار قابل بازگشت نیست.").setNegativeButton("انصراف",null).setPositiveButton("پاک شود"){_,_->db.clearSales();showInvoice()}.show()})
 }
 fun exportXlsx(rows:List<InvoiceRow>){try{val name="sibfood-invoice.xlsx";val resolver=contentResolver;val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")};val uri=if(Build.VERSION.SDK_INT>=29)resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) else null
 if(uri==null){toast("ذخیره در Downloads در این نسخه اندروید پشتیبانی نشد");return};resolver.openOutputStream(uri).use{out->writeXlsx(out!!,rows)};toast("فایل در پوشه Downloads ذخیره شد")}catch(e:Exception){toast("خطا در ساخت اکسل: ${e.message}")}}
 fun writeXlsx(out:OutputStream,rows:List<InvoiceRow>){val z=ZipOutputStream(out);fun entry(n:String,s:String){z.putNextEntry(ZipEntry(n));z.write(s.toByteArray(StandardCharsets.UTF_8));z.closeEntry()}
 entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
 entry("_rels/.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
 entry("xl/workbook.xml","""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="فاکتور روز" sheetId="1" r:id="rId1"/></sheets></workbook>""")
 entry("xl/_rels/workbook.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
 val all= mutableListOf(listOf("نام کالا","مجموع تعداد","میانگین قیمت واحد","مبلغ کل"));rows.forEach{all.add(listOf(it.name,it.qty.toString(),it.avg.toString(),it.total.toString()))};all.add(listOf("جمع کل","","",rows.sumOf{it.total}.toString()))
 fun col(i:Int):String{var x=i+1;var s="";while(x>0){val r=(x-1)%26;s=('A'+r)+s;x=(x-1)/26};return s}
 val sb=StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
 all.forEachIndexed{ri,row->sb.append("<row r=\"${ri+1}\">");row.forEachIndexed{ci,v->val ref="${col(ci)}${ri+1}";val num=v.toLongOrNull();if(num!=null)sb.append("<c r=\"$ref\"><v>$num</v></c>") else sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")}</t></is></c>")};sb.append("</row>")};sb.append("</sheetData></worksheet>")
 entry("xl/worksheets/sheet1.xml",sb.toString());z.finish();z.close()
 }
 fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
